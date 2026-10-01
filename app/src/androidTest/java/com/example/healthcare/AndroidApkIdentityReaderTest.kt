package com.example.healthcare

import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.appupdate.AndroidApkIdentityReader
import com.example.healthcare.data.appupdate.ApkIdentityReadResult
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.seed.BundledFoodDataSeeder
import java.io.File
import java.util.Properties
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidApkIdentityReaderTest {
    @Before
    fun awaitBundledDatabaseInitialization() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manifest = context.assets.open(
            "${BundledFoodDataSeeder.ASSET_DIRECTORY}/${BundledFoodDataSeeder.MANIFEST_FILE}"
        ).bufferedReader().use { reader -> Properties().apply { load(reader) } }
        val expectedFoodCount = manifest.getProperty("totalFoodCount").toInt()
        val expectedTemplateCount = manifest.getProperty("templateCount").toInt()
        val expectedIngredientCount = manifest.getProperty("ingredientCount").toInt()
        val expectedBundleId = manifest.getProperty("bundleId").orEmpty()
        assertTrue(expectedFoodCount > 0)
        assertEquals(292, expectedTemplateCount)
        assertTrue(expectedIngredientCount > 0)
        assertTrue(expectedBundleId.isNotBlank())
        val database = AppDatabase.getDatabase(context)
        val seedPreferences = context.getSharedPreferences("bundled_food_seed", android.content.Context.MODE_PRIVATE)

        // The bundle stamp is published only after the seed transaction returns.
        // Register before checking its current value so either completion path is observed.
        withTimeout(180_000) {
            callbackFlow<String?> {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
                    if (key == "bundle_id") trySend(preferences.getString("bundle_id", null))
                }
                seedPreferences.registerOnSharedPreferenceChangeListener(listener)
                trySend(seedPreferences.getString("bundle_id", null))
                awaitClose { seedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
            }.first { it == expectedBundleId }
        }
        val foodCount = database.foodItemDao().count()
        val templateCount = database.mealCoachDao().templateCount()
        val ingredientCount = database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM meal_template_ingredients"
        ).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
        assertTrue("foodCount=$foodCount expected=$expectedFoodCount", foodCount >= expectedFoodCount)
        assertTrue("templateCount=$templateCount expected=$expectedTemplateCount", templateCount >= expectedTemplateCount)
        assertTrue("ingredientCount=$ingredientCount expected=$expectedIngredientCount", ingredientCount >= expectedIngredientCount)
    }

    @Test
    fun installedAndArchivePackageIdentityExposeMatchingSignerOnDeviceApi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reader = AndroidApkIdentityReader(context)
        val archive = reader.readArchive(copyInstalledApkToReadableArchive(context))
        val installed = reader.readInstalled(context.packageName)

        assertTrue("archive=$archive", archive is ApkIdentityReadResult.Success)
        assertTrue("installed=$installed", installed is ApkIdentityReadResult.Success)
        archive as ApkIdentityReadResult.Success
        installed as ApkIdentityReadResult.Success
        assertEquals(context.packageName, archive.identity.packageName)
        assertEquals(context.packageName, installed.identity.packageName)
        assertTrue(archive.identity.signerSha256.isNotEmpty())
        assertTrue(installed.identity.signerSha256.isNotEmpty())
        assertTrue(archive.identity.signerSha256.intersect(installed.identity.signerSha256).isNotEmpty())
    }

    @Test
    fun api29CompatibilityBranchUsesIntFlagsAndStillReadsBothSignerIdentities() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reader = AndroidApkIdentityReader(context, sdkInt = 29)
        val archive = reader.readArchive(copyInstalledApkToReadableArchive(context))
        val installed = reader.readInstalled(context.packageName)

        assertTrue("archive=$archive", archive is ApkIdentityReadResult.Success)
        assertTrue("installed=$installed", installed is ApkIdentityReadResult.Success)
        archive as ApkIdentityReadResult.Success
        installed as ApkIdentityReadResult.Success
        assertTrue(archive.identity.signerSha256.intersect(installed.identity.signerSha256).isNotEmpty())
    }

    @Test
    fun legacyGetSignaturesArchiveAndInstalledMatchModernIdentityOnApi29AndLater() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apk = copyInstalledApkToReadableArchive(context)
        val modernReader = AndroidApkIdentityReader(context, sdkInt = 29)
        // Force the real GET_SIGNATURES path even when modern queries succeed on API29+.
        // This exercises the same legacy query used by the modern-to-legacy fallback.
        val legacyReader = AndroidApkIdentityReader(context, sdkInt = 27)
        val modernArchive = modernReader.readArchive(apk)
        val modernInstalled = modernReader.readInstalled(context.packageName)
        val legacyArchive = legacyReader.readArchive(apk)
        val legacyInstalled = legacyReader.readInstalled(context.packageName)

        assertTrue("modernArchive=$modernArchive", modernArchive is ApkIdentityReadResult.Success)
        assertTrue("modernInstalled=$modernInstalled", modernInstalled is ApkIdentityReadResult.Success)
        assertTrue("legacyArchive=$legacyArchive", legacyArchive is ApkIdentityReadResult.Success)
        assertTrue("legacyInstalled=$legacyInstalled", legacyInstalled is ApkIdentityReadResult.Success)
        modernArchive as ApkIdentityReadResult.Success
        modernInstalled as ApkIdentityReadResult.Success
        legacyArchive as ApkIdentityReadResult.Success
        legacyInstalled as ApkIdentityReadResult.Success

        listOf(modernArchive, modernInstalled, legacyArchive, legacyInstalled).forEach { result ->
            assertEquals(context.packageName, result.identity.packageName)
            assertEquals(modernInstalled.identity.versionCode, result.identity.versionCode)
            assertTrue(result.identity.signerSha256.isNotEmpty())
        }
        assertTrue(modernArchive.identity.signerSha256.intersect(modernInstalled.identity.signerSha256).isNotEmpty())
        assertTrue(legacyArchive.identity.signerSha256.intersect(legacyInstalled.identity.signerSha256).isNotEmpty())
        assertTrue(legacyArchive.identity.signerSha256.intersect(modernArchive.identity.signerSha256).isNotEmpty())
        assertTrue(legacyInstalled.identity.signerSha256.intersect(modernInstalled.identity.signerSha256).isNotEmpty())
    }

    private fun copyInstalledApkToReadableArchive(context: android.content.Context): File {
        val archive = File(context.cacheDir, "identity-reader-test.apk")
        File(context.applicationInfo.sourceDir).copyTo(archive, overwrite = true)
        return archive
    }
}

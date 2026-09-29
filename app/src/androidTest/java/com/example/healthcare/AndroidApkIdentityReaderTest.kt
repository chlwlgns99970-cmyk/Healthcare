package com.example.healthcare

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.appupdate.AndroidApkIdentityReader
import com.example.healthcare.data.appupdate.ApkIdentityReadResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidApkIdentityReaderTest {
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

    private fun copyInstalledApkToReadableArchive(context: android.content.Context): File {
        val archive = File(context.cacheDir, "identity-reader-test.apk")
        File(context.applicationInfo.sourceDir).copyTo(archive, overwrite = true)
        return archive
    }
}

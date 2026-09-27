package com.example.healthcare

import com.example.healthcare.data.appupdate.APP_UPDATE_CHECK_INTERVAL_MILLIS
import com.example.healthcare.data.appupdate.ApkIdentity
import com.example.healthcare.data.appupdate.ApkIdentityReader
import com.example.healthcare.data.appupdate.ApkUpdateVerifier
import com.example.healthcare.data.appupdate.ApkVerificationFailure
import com.example.healthcare.data.appupdate.ApkVerificationResult
import com.example.healthcare.data.appupdate.AppReleaseMetadata
import com.example.healthcare.data.appupdate.AppReleaseMetadataValidator
import com.example.healthcare.data.appupdate.AppUpdateCheckResult
import com.example.healthcare.data.appupdate.AppUpdateChecker
import com.example.healthcare.data.appupdate.AppUpdatePolicy
import com.example.healthcare.data.appupdate.AppUpdatePreferenceStore
import com.example.healthcare.data.appupdate.AppUpdateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppUpdateTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun serverVersionAboveCurrentIsAvailable() = runBlocking {
        assertTrue(checker(serverVersion = 5, currentVersion = 4).check(manual = true) is AppUpdateCheckResult.Available)
    }

    @Test
    fun serverVersionEqualCurrentIsLatest() = runBlocking {
        assertEquals(AppUpdateCheckResult.UpToDate, checker(4, 4).check(manual = true))
    }

    @Test
    fun serverVersionBelowCurrentIsLatest() = runBlocking {
        assertEquals(AppUpdateCheckResult.UpToDate, checker(3, 4).check(manual = true))
    }

    @Test
    fun malformedMetadataIsRejected() = runBlocking {
        val invalid = release(versionCode = 5).copy(apkUrl = "http://example.com/update.apk")
        val result = checker(repository = FakeRepository(invalid), currentVersion = 4).check(manual = true)
        assertEquals(AppUpdateCheckResult.Failed, result)
    }

    @Test
    fun sha256MatchPassesVerification() {
        assertTrue(verify().first is ApkVerificationResult.Valid)
    }

    @Test
    fun sha256MismatchIsRejected() {
        val (result) = verify(expectedHash = "0".repeat(64))
        assertEquals(ApkVerificationFailure.SHA256_MISMATCH, (result as ApkVerificationResult.Invalid).failure)
    }

    @Test
    fun matchingPackagePassesVerification() {
        assertTrue(verify(archivePackage = PRODUCT_PACKAGE).first is ApkVerificationResult.Valid)
    }

    @Test
    fun mismatchingPackageIsRejected() {
        val (result) = verify(archivePackage = "com.example.other")
        assertEquals(ApkVerificationFailure.PACKAGE_MISMATCH, (result as ApkVerificationResult.Invalid).failure)
    }

    @Test
    fun higherApkVersionPassesVerification() {
        assertTrue(verify(archiveVersion = 5, metadataVersion = 5, currentVersion = 4).first is ApkVerificationResult.Valid)
    }

    @Test
    fun apkAndMetadataVersionMismatchIsRejected() {
        val (result) = verify(archiveVersion = 5, metadataVersion = 6, currentVersion = 4)
        assertEquals(
            ApkVerificationFailure.VERSION_METADATA_MISMATCH,
            (result as ApkVerificationResult.Invalid).failure
        )
    }

    @Test
    fun nonIncreasingApkVersionIsRejected() {
        val (result) = verify(archiveVersion = 4, metadataVersion = 4, currentVersion = 4)
        assertEquals(ApkVerificationFailure.VERSION_NOT_HIGHER, (result as ApkVerificationResult.Invalid).failure)
    }

    @Test
    fun matchingSignerPassesVerification() {
        assertTrue(verify(archiveSigners = setOf(RELEASE_SIGNER)).first is ApkVerificationResult.Valid)
    }

    @Test
    fun mismatchingSignerIsRejected() {
        val (result) = verify(archiveSigners = setOf("B".repeat(64)))
        assertEquals(ApkVerificationFailure.SIGNER_MISMATCH, (result as ApkVerificationResult.Invalid).failure)
    }

    @Test
    fun installedSignerMustAlsoMatchKnownReleaseSigner() {
        val (result) = verify(installedSigners = setOf("B".repeat(64)))
        assertEquals(ApkVerificationFailure.SIGNER_MISMATCH, (result as ApkVerificationResult.Invalid).failure)
    }

    @Test
    fun automaticCheckIsThrottledForTwentyFourHours() = runBlocking {
        val repository = FakeRepository(release(5))
        val preferences = FakePreferences(lastSuccessfulCheckAt = 1_000L)
        val checker = AppUpdateChecker(repository, preferences, 4) { 1_000L + APP_UPDATE_CHECK_INTERVAL_MILLIS - 1L }
        assertEquals(AppUpdateCheckResult.Throttled, checker.check(manual = false))
        assertEquals(0, repository.calls)
    }

    @Test
    fun manualCheckIgnoresThrottle() = runBlocking {
        val repository = FakeRepository(release(5))
        val preferences = FakePreferences(lastSuccessfulCheckAt = 1_000L)
        val checker = AppUpdateChecker(repository, preferences, 4) { 1_001L }
        assertTrue(checker.check(manual = true) is AppUpdateCheckResult.Available)
        assertEquals(1, repository.calls)
    }

    @Test
    fun networkFailureDoesNotEscapeChecker() = runBlocking {
        val checker = AppUpdateChecker(FailingRepository(), FakePreferences(), 4)
        assertEquals(AppUpdateCheckResult.Failed, checker.check(manual = true))
    }

    @Test
    fun laterPreventsImmediateRepromptInSameSession() = runBlocking {
        var now = 1_000L
        val checker = AppUpdateChecker(FakeRepository(release(5)), FakePreferences(), 4) { now }
        val first = checker.check(manual = true) as AppUpdateCheckResult.Available
        checker.dismissForSession(first.release.versionCode)
        now += APP_UPDATE_CHECK_INTERVAL_MILLIS
        assertEquals(AppUpdateCheckResult.DismissedForSession, checker.check(manual = false))
    }

    @Test
    fun metadataRejectsPathTraversalFileName() {
        assertFalse(AppReleaseMetadataValidator.isValid(release(5).copy(fileName = "../update.apk")))
    }

    @Test
    fun exactlyTwentyFourHoursAllowsAutomaticCheck() {
        assertTrue(AppUpdatePolicy.shouldCheckAutomatically(1_000L, 1_000L + APP_UPDATE_CHECK_INTERVAL_MILLIS))
    }

    private fun checker(
        serverVersion: Int = 5,
        currentVersion: Int,
        repository: AppUpdateRepository = FakeRepository(release(serverVersion))
    ) = AppUpdateChecker(repository, FakePreferences(), currentVersion) { 50_000L }

    private fun verify(
        archivePackage: String = PRODUCT_PACKAGE,
        archiveVersion: Long = 5,
        metadataVersion: Int = 5,
        currentVersion: Long = 4,
        archiveSigners: Set<String> = setOf(RELEASE_SIGNER),
        installedSigners: Set<String> = setOf(RELEASE_SIGNER),
        expectedHash: String? = null
    ): Pair<ApkVerificationResult, File> {
        val apk = temporaryFolder.newFile().apply { writeBytes("verified-apk-fixture".toByteArray()) }
        val hash = expectedHash ?: ApkUpdateVerifier.sha256(apk)
        val reader = FakeIdentityReader(
            ApkIdentity(archivePackage, archiveVersion, archiveSigners)
        )
        val verifier = ApkUpdateVerifier(reader, PRODUCT_PACKAGE, RELEASE_SIGNER)
        val metadata = release(metadataVersion).copy(sha256 = hash)
        return verifier.verify(apk, metadata, currentVersion, installedSigners) to apk
    }

    private class FakeRepository(private val release: AppReleaseMetadata) : AppUpdateRepository {
        var calls: Int = 0
        override suspend fun getLatestRelease(): AppReleaseMetadata {
            calls++
            return release
        }
    }

    private class FailingRepository : AppUpdateRepository {
        override suspend fun getLatestRelease(): AppReleaseMetadata = error("offline")
    }

    private class FakePreferences(
        override var lastSuccessfulCheckAt: Long = 0L,
        override var lastPromptedVersionCode: Int = 0
    ) : AppUpdatePreferenceStore {
        override fun recordSuccessfulCheck(atMillis: Long) {
            lastSuccessfulCheckAt = atMillis
        }

        override fun recordPromptedVersion(versionCode: Int) {
            lastPromptedVersionCode = versionCode
        }
    }

    private class FakeIdentityReader(private val archive: ApkIdentity?) : ApkIdentityReader {
        override fun readArchive(apkFile: File): ApkIdentity? = archive
        override fun readInstalled(packageName: String): ApkIdentity? = null
    }

    private companion object {
        const val PRODUCT_PACKAGE = "com.example.healthcare"
        const val RELEASE_SIGNER =
            "385693830FF4C9F9122A9DC5D992646A871AC82764439AEC1C1078BF55496CA8"

        fun release(versionCode: Int): AppReleaseMetadata = AppReleaseMetadata(
            versionName = "1.0.$versionCode",
            versionCode = versionCode,
            apkUrl = "https://github.com/example/app/releases/download/v$versionCode/update.apk",
            fileName = "update.apk",
            fileSizeBytes = 123L,
            sha256 = "A".repeat(64),
            releaseNotes = listOf("Update"),
            releasedAt = "2026-09-27T00:00:00Z"
        )
    }
}

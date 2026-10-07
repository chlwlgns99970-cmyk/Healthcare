package com.example.healthcare

import com.example.healthcare.data.appupdate.ApkIdentity
import com.example.healthcare.data.appupdate.ApkIdentityReadFailure
import com.example.healthcare.data.appupdate.ApkIdentityReadResult
import com.example.healthcare.data.appupdate.ApkIdentityReader
import com.example.healthcare.data.appupdate.ApkSigningCompatibility
import com.example.healthcare.data.appupdate.ApkUpdateVerifier
import com.example.healthcare.data.appupdate.ApkVerificationFailure
import com.example.healthcare.data.appupdate.ApkVerificationResult
import com.example.healthcare.data.appupdate.AppReleaseMetadata
import com.example.healthcare.data.appupdate.AppReleaseMetadataValidator
import com.example.healthcare.data.appupdate.AppUpdateCheckResult
import com.example.healthcare.data.appupdate.AppUpdateChecker
import com.example.healthcare.data.appupdate.AppUpdatePreferenceStore
import com.example.healthcare.data.appupdate.AppUpdateRepository
import com.example.healthcare.data.appupdate.SigningQueryMode
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
        assertTrue(checker(serverVersion = 5, currentVersion = 4).check(manual = false) is AppUpdateCheckResult.Available)
    }

    @Test
    fun serverVersionEqualCurrentIsLatest() = runBlocking {
        assertEquals(AppUpdateCheckResult.UpToDate, checker(4, 4).check(manual = false))
    }

    @Test
    fun serverVersionBelowCurrentIsLatest() = runBlocking {
        assertEquals(AppUpdateCheckResult.UpToDate, checker(3, 4).check(manual = false))
    }

    @Test
    fun malformedMetadataIsRejected() = runBlocking {
        val invalid = release(versionCode = 5).copy(apkUrl = "http://example.com/update.apk")
        val result = checker(repository = FakeRepository(invalid), currentVersion = 4).check(manual = false)
        assertEquals(AppUpdateCheckResult.Failed, result)
    }

    @Test
    fun fileSizeMatchPassesVerification() {
        assertTrue(verify().first is ApkVerificationResult.Success)
    }

    @Test
    fun fileSizeMismatchIsRejectedBeforeHashing() {
        val (result, apk) = verify(expectedSize = 999L)
        val failure = result as ApkVerificationResult.Failure
        assertEquals(ApkVerificationFailure.FILE_SIZE_MISMATCH, failure.reason)
        assertEquals(apk.length(), failure.diagnostics.actualSize)
    }

    @Test
    fun missingFileIsReportedSeparately() {
        val apk = File(temporaryFolder.root, "missing.apk")
        val result = verifier(validReader()).verify(apk, release(5), currentVersionCode = 4)
        assertEquals(
            ApkVerificationFailure.FILE_NOT_FOUND,
            (result as ApkVerificationResult.Failure).reason
        )
    }

    @Test
    fun sha256MatchPassesVerification() {
        assertTrue(verify().first is ApkVerificationResult.Success)
    }

    @Test
    fun sha256MismatchIsRejected() {
        val (result) = verify(expectedHash = "0".repeat(64))
        assertEquals(
            ApkVerificationFailure.HASH_MISMATCH,
            (result as ApkVerificationResult.Failure).reason
        )
    }

    @Test
    fun matchingPackagePassesVerification() {
        assertTrue(verify(archivePackage = PRODUCT_PACKAGE).first is ApkVerificationResult.Success)
    }

    @Test
    fun mismatchingPackageIsRejected() {
        val (result) = verify(archivePackage = "com.example.other")
        assertEquals(
            ApkVerificationFailure.PACKAGE_MISMATCH,
            (result as ApkVerificationResult.Failure).reason
        )
    }

    @Test
    fun higherApkVersionPassesVerification() {
        assertTrue(
            verify(archiveVersion = 5, metadataVersion = 5, currentVersion = 4).first
                is ApkVerificationResult.Success
        )
    }

    @Test
    fun apkAndMetadataVersionMismatchIsRejected() {
        val (result) = verify(archiveVersion = 5, metadataVersion = 6, currentVersion = 4)
        assertEquals(
            ApkVerificationFailure.VERSION_MISMATCH,
            (result as ApkVerificationResult.Failure).reason
        )
    }

    @Test
    fun nonIncreasingApkVersionIsRejected() {
        val (result) = verify(archiveVersion = 4, metadataVersion = 4, currentVersion = 4)
        assertEquals(
            ApkVerificationFailure.VERSION_MISMATCH,
            (result as ApkVerificationResult.Failure).reason
        )
    }

    @Test
    fun matchingSignerPassesVerification() {
        assertTrue(verify(archiveSigners = setOf(RELEASE_SIGNER)).first is ApkVerificationResult.Success)
    }

    @Test
    fun signingHistoryDoesNotCauseFalseMismatchWhenKnownSignerIsPresent() {
        val oldSigner = "C".repeat(64)
        val (result) = verify(
            archiveSigners = setOf(RELEASE_SIGNER, oldSigner),
            installedSigners = setOf(RELEASE_SIGNER)
        )
        assertTrue(result is ApkVerificationResult.Success)
    }

    @Test
    fun signerReadFailureIsNotReportedAsMismatch() {
        val (result) = verify(
            archiveResult = ApkIdentityReadResult.Failure(
                ApkIdentityReadFailure.SIGNER_READ_FAILED,
                "OEM did not expose archive signer"
            )
        )
        assertEquals(
            ApkVerificationFailure.SIGNER_READ_FAILED,
            (result as ApkVerificationResult.Failure).reason
        )
    }

    @Test
    fun mismatchingSignerIsRejected() {
        val (result) = verify(archiveSigners = setOf("B".repeat(64)))
        assertEquals(
            ApkVerificationFailure.SIGNER_MISMATCH,
            (result as ApkVerificationResult.Failure).reason
        )
    }

    @Test
    fun installedSignerMustAlsoMatchKnownReleaseSigner() {
        val (result) = verify(installedSigners = setOf("B".repeat(64)))
        assertEquals(
            ApkVerificationFailure.SIGNER_MISMATCH,
            (result as ApkVerificationResult.Failure).reason
        )
    }

    @Test
    fun archiveParseFailureIsReportedSeparately() {
        val (result) = verify(
            archiveResult = ApkIdentityReadResult.Failure(ApkIdentityReadFailure.APK_PARSE_FAILED)
        )
        assertEquals(
            ApkVerificationFailure.APK_PARSE_FAILED,
            (result as ApkVerificationResult.Failure).reason
        )
    }

    @Test
    fun api27UsesLegacySignatureQuery() {
        assertEquals(listOf(SigningQueryMode.LEGACY), ApkSigningCompatibility.queryModes(27))
    }

    @Test
    fun api28Through32TryModernThenLegacySignatureQuery() {
        assertEquals(
            listOf(SigningQueryMode.MODERN, SigningQueryMode.LEGACY),
            ApkSigningCompatibility.queryModes(28)
        )
        assertEquals(
            listOf(SigningQueryMode.MODERN, SigningQueryMode.LEGACY),
            ApkSigningCompatibility.queryModes(29)
        )
        assertEquals(
            listOf(SigningQueryMode.MODERN, SigningQueryMode.LEGACY),
            ApkSigningCompatibility.queryModes(32)
        )
    }

    @Test
    fun api33AndLaterTryModernThenLegacySignatureQuery() {
        assertEquals(
            listOf(SigningQueryMode.MODERN, SigningQueryMode.LEGACY),
            ApkSigningCompatibility.queryModes(33)
        )
        assertEquals(
            listOf(SigningQueryMode.MODERN, SigningQueryMode.LEGACY),
            ApkSigningCompatibility.queryModes(36)
        )
    }

    @Test
    fun automaticCheckIgnoresRecentSuccessfulCheckAndFetches() = runBlocking {
        val repository = FakeRepository(release(5))
        val preferences = FakePreferences(lastSuccessfulCheckAt = 1_000L)
        val checker = AppUpdateChecker(repository, preferences, 4) {
            1_001L
        }
        assertTrue(checker.check(manual = false) is AppUpdateCheckResult.Available)
        assertEquals(1, repository.calls)
    }

    @Test
    fun manualCheckStillFetchesLatestRelease() = runBlocking {
        val repository = FakeRepository(release(5))
        val preferences = FakePreferences(lastSuccessfulCheckAt = 1_000L)
        val checker = AppUpdateChecker(repository, preferences, 4) { 1_001L }
        assertTrue(checker.check(manual = true) is AppUpdateCheckResult.Available)
        assertEquals(1, repository.calls)
    }

    @Test
    fun networkFailureDoesNotEscapeChecker() = runBlocking {
        val checker = AppUpdateChecker(FailingRepository(), FakePreferences(), 4)
        assertEquals(AppUpdateCheckResult.Failed, checker.check(manual = false))
    }

    @Test
    fun laterPreventsImmediateRepromptInSameSession() = runBlocking {
        val checker = AppUpdateChecker(FakeRepository(release(5)), FakePreferences(), 4) { 1_000L }
        val first = checker.check(manual = false) as AppUpdateCheckResult.Available
        checker.dismissForSession(first.release.versionCode)
        assertEquals(AppUpdateCheckResult.DismissedForSession, checker.check(manual = false))
    }

    @Test
    fun newColdStartChecksAgainAndCanPromptAfterLaterWasSelected() = runBlocking {
        val repository = FakeRepository(release(5))
        val preferences = FakePreferences()
        val firstProcess = AppUpdateChecker(repository, preferences, 4) { 1_000L }
        val first = firstProcess.check(manual = false) as AppUpdateCheckResult.Available
        firstProcess.dismissForSession(first.release.versionCode)

        val nextProcess = AppUpdateChecker(repository, preferences, 4) { 1_001L }
        assertTrue(nextProcess.check(manual = false) is AppUpdateCheckResult.Available)
        assertEquals(2, repository.calls)
    }

    @Test
    fun metadataRejectsPathTraversalFileName() {
        assertFalse(AppReleaseMetadataValidator.isValid(release(5).copy(fileName = "../update.apk")))
    }

    @Test
    fun twoColdStartCheckersBothFetchWithinTwentyFourHours() = runBlocking {
        val repository = FakeRepository(release(4))
        val preferences = FakePreferences(lastSuccessfulCheckAt = 1_000L)
        assertEquals(
            AppUpdateCheckResult.UpToDate,
            AppUpdateChecker(repository, preferences, 4) { 1_001L }.check(manual = false)
        )
        assertEquals(
            AppUpdateCheckResult.UpToDate,
            AppUpdateChecker(repository, preferences, 4) { 1_002L }.check(manual = false)
        )
        assertEquals(2, repository.calls)
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
        expectedHash: String? = null,
        expectedSize: Long? = null,
        archiveResult: ApkIdentityReadResult? = null
    ): Pair<ApkVerificationResult, File> {
        val apk = temporaryFolder.newFile().apply { writeBytes("verified-apk-fixture".toByteArray()) }
        val hash = expectedHash ?: ApkUpdateVerifier.sha256(apk)
        val reader = FakeIdentityReader(
            archiveResult = archiveResult ?: ApkIdentityReadResult.Success(
                ApkIdentity(archivePackage, archiveVersion, archiveSigners)
            ),
            installedResult = ApkIdentityReadResult.Success(
                ApkIdentity(PRODUCT_PACKAGE, currentVersion, installedSigners)
            )
        )
        val metadata = release(metadataVersion).copy(
            sha256 = hash,
            fileSizeBytes = expectedSize ?: apk.length()
        )
        return verifier(reader).verify(apk, metadata, currentVersion) to apk
    }

    private fun validReader() = FakeIdentityReader(
        archiveResult = ApkIdentityReadResult.Success(ApkIdentity(PRODUCT_PACKAGE, 5, setOf(RELEASE_SIGNER))),
        installedResult = ApkIdentityReadResult.Success(ApkIdentity(PRODUCT_PACKAGE, 4, setOf(RELEASE_SIGNER)))
    )

    private fun verifier(reader: ApkIdentityReader) =
        ApkUpdateVerifier(reader, PRODUCT_PACKAGE, RELEASE_SIGNER)

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

    private class FakeIdentityReader(
        private val archiveResult: ApkIdentityReadResult,
        private val installedResult: ApkIdentityReadResult
    ) : ApkIdentityReader {
        override fun readArchive(apkFile: File): ApkIdentityReadResult = archiveResult
        override fun readInstalled(packageName: String): ApkIdentityReadResult = installedResult
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

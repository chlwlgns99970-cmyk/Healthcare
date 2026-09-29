package com.example.healthcare

import com.example.healthcare.data.appupdate.ApkIdentity
import com.example.healthcare.data.appupdate.ApkIdentityReadFailure
import com.example.healthcare.data.appupdate.ApkIdentityReadResult
import com.example.healthcare.data.appupdate.ApkIdentityReader
import com.example.healthcare.data.appupdate.ApkUpdateVerifier
import com.example.healthcare.data.appupdate.ApkVerificationFailure
import com.example.healthcare.data.appupdate.AppReleaseMetadata
import com.example.healthcare.data.appupdate.AppUpdateManager
import com.example.healthcare.data.appupdate.AppUpdatePhase
import com.example.healthcare.data.appupdate.AppUpdatePreferenceStore
import com.example.healthcare.data.appupdate.AppUpdateRepository
import com.example.healthcare.data.appupdate.PrivateUpdateApkDownloader
import com.example.healthcare.data.appupdate.SystemAppInstaller
import com.example.healthcare.data.appupdate.UpdateApkDownloader
import com.example.healthcare.data.appupdate.UpdateDownloadException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateDownloadTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun completeDownloadIsFinalizedAndReportsOneHundredPercent() {
        val server = MockWebServer().also { it.start() }
        try {
            val payload = "complete-apk".toByteArray()
            server.enqueue(MockResponse().setResponseCode(200).setBody(payload.decodeToString()))
            val progress = mutableListOf<Int>()
            val downloader = PrivateUpdateApkDownloader(
                File(temporaryFolder.root, "updates"),
                OkHttpClient()
            )

            val file = kotlinx.coroutines.runBlocking {
                downloader.download(release(server.url("/asset.apk").toString(), payload)) {
                    progress += it
                }
            }

            assertTrue(file.exists())
            assertEquals(payload.size.toLong(), file.length())
            assertEquals(100, progress.last())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun responseLengthMismatchIsReportedBeforeVerification() {
        val server = MockWebServer().also { it.start() }
        try {
            val payload = "short".toByteArray()
            server.enqueue(MockResponse().setResponseCode(200).setBody(payload.decodeToString()))
            val downloader = PrivateUpdateApkDownloader(
                File(temporaryFolder.root, "updates"),
                OkHttpClient()
            )
            val metadata = release(server.url("/asset.apk").toString(), payload)
                .copy(fileSizeBytes = payload.size + 10L)

            val error = runCatching {
                kotlinx.coroutines.runBlocking { downloader.download(metadata) {} }
            }.exceptionOrNull() as UpdateDownloadException

            assertEquals(ApkVerificationFailure.FILE_SIZE_MISMATCH, error.failure)
            assertFalse(File(temporaryFolder.root, "updates/update-6.apk.part").exists())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun chunkedShortBodyIsReportedAsIncompleteDownload() {
        val server = MockWebServer().also { it.start() }
        try {
            val payload = "short".toByteArray()
            server.enqueue(MockResponse().setChunkedBody(payload.decodeToString(), 2))
            val downloader = PrivateUpdateApkDownloader(
                File(temporaryFolder.root, "updates"),
                OkHttpClient()
            )
            val metadata = release(server.url("/asset.apk").toString(), payload)
                .copy(fileSizeBytes = payload.size + 10L)

            val error = runCatching {
                kotlinx.coroutines.runBlocking { downloader.download(metadata) {} }
            }.exceptionOrNull() as UpdateDownloadException

            assertEquals(ApkVerificationFailure.DOWNLOAD_INCOMPLETE, error.failure)
            assertEquals(payload.size.toLong(), error.actualSize)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun redirectsAndChunkedResponsesAreCompletelySaved() {
        val server = MockWebServer().also { it.start() }
        try {
            val payload = "redirected-chunked-apk".toByteArray()
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", server.url("/final.apk"))
            )
            server.enqueue(MockResponse().setChunkedBody(payload.decodeToString(), 3))
            val downloader = PrivateUpdateApkDownloader(
                File(temporaryFolder.root, "updates"),
                OkHttpClient()
            )

            val file = kotlinx.coroutines.runBlocking {
                downloader.download(release(server.url("/redirect").toString(), payload)) {}
            }

            assertEquals(payload.toList(), file.readBytes().toList())
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun retryDeletesFailedFileAndDownloadsAgain() = runTest {
        val payload = "retry-apk".toByteArray()
        val apk = File(temporaryFolder.root, "update.apk")
        val metadata = release("https://example.com/update.apk", payload)
        val downloader = RetryDownloader(apk, payload)
        val reader = RetryIdentityReader()
        val manager = AppUpdateManager(
            repository = FixedRepository(metadata),
            preferences = MemoryPreferences(),
            downloader = downloader,
            verifier = ApkUpdateVerifier(reader, PRODUCT_PACKAGE, RELEASE_SIGNER),
            installer = SystemAppInstaller("unused.test.authority"),
            scope = this,
            currentVersionCode = 5,
            currentPackageName = PRODUCT_PACKAGE,
            expectedPackageName = PRODUCT_PACKAGE,
            installEnabled = true,
            showTechnicalFailureReason = true,
            sdkInt = 32,
            logger = { _, _ -> }
        )

        manager.checkManually()
        advanceUntilIdle()
        manager.startDownload()
        advanceUntilIdle()

        assertEquals(AppUpdatePhase.FAILED, manager.state.value.phase)
        assertEquals(ApkVerificationFailure.SIGNER_READ_FAILED, manager.state.value.failureReason)
        assertTrue(manager.state.value.message.orEmpty().contains("SIGNER_READ_FAILED"))
        assertFalse(apk.exists())

        manager.retryDownload()
        advanceUntilIdle()

        assertEquals(2, downloader.downloadCalls)
        assertTrue(downloader.clearCalls >= 2)
        assertEquals(AppUpdatePhase.READY_TO_INSTALL, manager.state.value.phase)
    }

    @Test
    fun productFailureMessageDoesNotExposeTechnicalEnum() = runTest {
        val payload = "product-message-apk".toByteArray()
        val apk = File(temporaryFolder.root, "product-update.apk")
        val metadata = release("https://example.com/update.apk", payload)
        val manager = AppUpdateManager(
            repository = FixedRepository(metadata),
            preferences = MemoryPreferences(),
            downloader = RetryDownloader(apk, payload),
            verifier = ApkUpdateVerifier(
                object : ApkIdentityReader {
                    override fun readArchive(apkFile: File) = ApkIdentityReadResult.Failure(
                        ApkIdentityReadFailure.SIGNER_READ_FAILED
                    )
                    override fun readInstalled(packageName: String) =
                        ApkIdentityReadResult.Success(ApkIdentity(PRODUCT_PACKAGE, 5, setOf(RELEASE_SIGNER)))
                },
                PRODUCT_PACKAGE,
                RELEASE_SIGNER
            ),
            installer = SystemAppInstaller("unused.test.authority"),
            scope = this,
            currentVersionCode = 5,
            currentPackageName = PRODUCT_PACKAGE,
            expectedPackageName = PRODUCT_PACKAGE,
            installEnabled = true,
            showTechnicalFailureReason = false,
            sdkInt = 29,
            logger = { _, _ -> }
        )

        manager.checkManually()
        advanceUntilIdle()
        manager.startDownload()
        advanceUntilIdle()

        assertEquals(ApkVerificationFailure.SIGNER_READ_FAILED, manager.state.value.failureReason)
        assertFalse(manager.state.value.message.orEmpty().contains("SIGNER_READ_FAILED"))
    }

    private fun release(url: String, payload: ByteArray): AppReleaseMetadata {
        val fixture = File(temporaryFolder.root, "hash-source-${payload.size}.apk").apply { writeBytes(payload) }
        return AppReleaseMetadata(
            versionName = "1.0.5",
            versionCode = 6,
            apkUrl = url,
            fileName = "today-mwo-meokji-v1.0.5.apk",
            fileSizeBytes = payload.size.toLong(),
            sha256 = ApkUpdateVerifier.sha256(fixture),
            releaseNotes = listOf("업데이트 호환성 개선"),
            releasedAt = "2026-09-28T00:00:00Z"
        )
    }

    private class FixedRepository(private val metadata: AppReleaseMetadata) : AppUpdateRepository {
        override suspend fun getLatestRelease(): AppReleaseMetadata = metadata
    }

    private class MemoryPreferences : AppUpdatePreferenceStore {
        override var lastSuccessfulCheckAt: Long = 0L
        override var lastPromptedVersionCode: Int = 0
        override fun recordSuccessfulCheck(atMillis: Long) {
            lastSuccessfulCheckAt = atMillis
        }

        override fun recordPromptedVersion(versionCode: Int) {
            lastPromptedVersionCode = versionCode
        }
    }

    private class RetryDownloader(
        private val file: File,
        private val payload: ByteArray
    ) : UpdateApkDownloader {
        var downloadCalls = 0
        var clearCalls = 0

        override suspend fun download(metadata: AppReleaseMetadata, onProgress: (Int) -> Unit): File {
            downloadCalls++
            file.writeBytes(payload)
            onProgress(100)
            return file
        }

        override fun delete(file: File?) {
            file?.delete()
        }

        override fun clear() {
            clearCalls++
            file.delete()
        }
    }

    private class RetryIdentityReader : ApkIdentityReader {
        private var archiveReads = 0

        override fun readArchive(apkFile: File): ApkIdentityReadResult {
            archiveReads++
            return if (archiveReads == 1) {
                ApkIdentityReadResult.Failure(ApkIdentityReadFailure.SIGNER_READ_FAILED)
            } else {
                ApkIdentityReadResult.Success(ApkIdentity(PRODUCT_PACKAGE, 6, setOf(RELEASE_SIGNER)))
            }
        }

        override fun readInstalled(packageName: String): ApkIdentityReadResult =
            ApkIdentityReadResult.Success(ApkIdentity(PRODUCT_PACKAGE, 5, setOf(RELEASE_SIGNER)))
    }

    private companion object {
        const val PRODUCT_PACKAGE = "com.example.healthcare"
        const val RELEASE_SIGNER =
            "385693830FF4C9F9122A9DC5D992646A871AC82764439AEC1C1078BF55496CA8"
    }
}

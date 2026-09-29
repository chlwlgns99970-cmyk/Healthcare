package com.example.healthcare.data.appupdate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

interface UpdateApkDownloader {
    suspend fun download(metadata: AppReleaseMetadata, onProgress: (Int) -> Unit): File
    fun delete(file: File?)
    fun clear()
}

class UpdateDownloadException(
    val failure: ApkVerificationFailure,
    val expectedSize: Long? = null,
    val actualSize: Long? = null,
    cause: Throwable? = null
) : Exception(failure.name, cause)

class PrivateUpdateApkDownloader internal constructor(
    private val updateDirectory: File,
    private val client: OkHttpClient
) : UpdateApkDownloader {
    constructor(context: Context, client: OkHttpClient) : this(
        File(context.filesDir, UPDATE_DIRECTORY),
        client
    )

    override suspend fun download(
        metadata: AppReleaseMetadata,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        clear()
        check(updateDirectory.mkdirs() || updateDirectory.isDirectory) { "Unable to prepare update directory" }
        val finalFile = File(updateDirectory, "update-${metadata.versionCode}.apk")
        val partialFile = File(updateDirectory, "update-${metadata.versionCode}.apk.part")
        val request = Request.Builder().url(metadata.apkUrl).get().build()
        try {
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "APK download HTTP ${response.code}" }
                val body = response.body ?: error("APK response body missing")
                val responseLength = body.contentLength()
                if (responseLength >= 0L && responseLength != metadata.fileSizeBytes) {
                    throw UpdateDownloadException(
                        failure = ApkVerificationFailure.FILE_SIZE_MISMATCH,
                        expectedSize = metadata.fileSizeBytes,
                        actualSize = responseLength
                    )
                }
                var copied = 0L
                var lastProgress = -1
                body.byteStream().use { input ->
                    partialFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            copied += count
                            val progress = ((copied * 100L) / metadata.fileSizeBytes)
                                .coerceIn(0L, 99L).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
                if (copied != metadata.fileSizeBytes) {
                    throw UpdateDownloadException(
                        failure = if (copied < metadata.fileSizeBytes) {
                            ApkVerificationFailure.DOWNLOAD_INCOMPLETE
                        } else {
                            ApkVerificationFailure.FILE_SIZE_MISMATCH
                        },
                        expectedSize = metadata.fileSizeBytes,
                        actualSize = copied
                    )
                }
            }
            check(partialFile.renameTo(finalFile)) { "Unable to finalize APK download" }
            onProgress(100)
            finalFile
        } catch (error: Throwable) {
            partialFile.delete()
            finalFile.delete()
            throw error
        }
    }

    override fun delete(file: File?) {
        file?.takeIf { it.parentFile == updateDirectory }?.delete()
    }

    override fun clear() {
        updateDirectory.listFiles()?.forEach { file ->
            if (file.isFile && (file.extension == "apk" || file.name.endsWith(".apk.part"))) {
                file.delete()
            }
        }
    }

    private companion object {
        const val UPDATE_DIRECTORY = "updates"
    }
}

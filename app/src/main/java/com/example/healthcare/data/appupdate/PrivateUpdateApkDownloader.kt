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
}

class PrivateUpdateApkDownloader(
    context: Context,
    private val client: OkHttpClient
) : UpdateApkDownloader {
    private val updateDirectory = File(context.filesDir, UPDATE_DIRECTORY)

    override suspend fun download(
        metadata: AppReleaseMetadata,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        check(AppReleaseMetadataValidator.isValid(metadata))
        updateDirectory.mkdirs()
        updateDirectory.listFiles()?.forEach(File::delete)
        val finalFile = File(updateDirectory, "update-${metadata.versionCode}.apk")
        val partialFile = File(updateDirectory, "update-${metadata.versionCode}.apk.part")
        val request = Request.Builder().url(metadata.apkUrl).get().build()
        try {
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "APK download HTTP ${response.code}" }
                val body = response.body ?: error("APK response body missing")
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
                check(copied == metadata.fileSizeBytes) { "APK size mismatch" }
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

    private companion object {
        const val UPDATE_DIRECTORY = "updates"
    }
}

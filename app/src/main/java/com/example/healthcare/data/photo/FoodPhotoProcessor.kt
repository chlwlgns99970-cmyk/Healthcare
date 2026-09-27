package com.example.healthcare.data.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FoodPhotoProcessingLimits {
    const val MAX_DIMENSION_PX = 1600
    const val MAX_UPLOAD_BYTES = 5L * 1024L * 1024L
    const val MAX_SOURCE_BYTES = 40L * 1024L * 1024L
    const val INITIAL_JPEG_QUALITY = 85
    const val MINIMUM_JPEG_QUALITY = 55
    const val JPEG_QUALITY_STEP = 10
    val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png")
}

sealed interface PhotoProcessingResult {
    data class Success(val file: File, val mimeType: String = "image/jpeg") : PhotoProcessingResult
    data object InvalidImage : PhotoProcessingResult
    data object UnsupportedFormat : PhotoProcessingResult
    data object TooLarge : PhotoProcessingResult
}

/** 사진을 업로드 전용 JPEG로 다시 인코딩해 회전값을 반영하고 EXIF를 제거합니다. */
class FoodPhotoProcessor(private val cacheDirectory: File) {
    suspend fun prepareForUpload(source: File): PhotoProcessingResult = withContext(Dispatchers.IO) {
        if (!source.isFile || source.length() <= 0L) {
            return@withContext PhotoProcessingResult.InvalidImage
        }
        if (source.length() > FoodPhotoProcessingLimits.MAX_SOURCE_BYTES) {
            return@withContext PhotoProcessingResult.TooLarge
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@withContext PhotoProcessingResult.InvalidImage
        }
        if (bounds.outMimeType !in FoodPhotoProcessingLimits.ALLOWED_MIME_TYPES) {
            return@withContext PhotoProcessingResult.UnsupportedFormat
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        }
        var workingBitmap = BitmapFactory.decodeFile(source.absolutePath, options)
            ?: return@withContext PhotoProcessingResult.InvalidImage

        val uploadDirectory = File(cacheDirectory, "food_photo_uploads").apply { mkdirs() }
        val output = runCatching { File.createTempFile("food_upload_", ".jpg", uploadDirectory) }
            .getOrElse {
                workingBitmap.recycle()
                return@withContext PhotoProcessingResult.InvalidImage
            }

        try {
            val rotation = readRotation(source)
            if (rotation != 0f) {
                val rotated = Bitmap.createBitmap(
                    workingBitmap,
                    0,
                    0,
                    workingBitmap.width,
                    workingBitmap.height,
                    Matrix().apply { postRotate(rotation) },
                    true
                )
                if (rotated !== workingBitmap) workingBitmap.recycle()
                workingBitmap = rotated
            }

            val largestSide = maxOf(workingBitmap.width, workingBitmap.height)
            if (largestSide > FoodPhotoProcessingLimits.MAX_DIMENSION_PX) {
                val scale = FoodPhotoProcessingLimits.MAX_DIMENSION_PX.toFloat() / largestSide
                val scaled = Bitmap.createScaledBitmap(
                    workingBitmap,
                    (workingBitmap.width * scale).toInt().coerceAtLeast(1),
                    (workingBitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== workingBitmap) workingBitmap.recycle()
                workingBitmap = scaled
            }

            var quality = FoodPhotoProcessingLimits.INITIAL_JPEG_QUALITY
            do {
                FileOutputStream(output, false).use { stream ->
                    if (!workingBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                        output.delete()
                        return@withContext PhotoProcessingResult.InvalidImage
                    }
                }
                quality -= FoodPhotoProcessingLimits.JPEG_QUALITY_STEP
            } while (
                output.length() > FoodPhotoProcessingLimits.MAX_UPLOAD_BYTES &&
                quality >= FoodPhotoProcessingLimits.MINIMUM_JPEG_QUALITY
            )

            if (output.length() <= 0L) {
                output.delete()
                PhotoProcessingResult.InvalidImage
            } else if (output.length() > FoodPhotoProcessingLimits.MAX_UPLOAD_BYTES) {
                output.delete()
                PhotoProcessingResult.TooLarge
            } else {
                PhotoProcessingResult.Success(output)
            }
        } catch (_: Exception) {
            output.delete()
            PhotoProcessingResult.InvalidImage
        } finally {
            if (!workingBitmap.isRecycled) workingBitmap.recycle()
        }
    }

    fun deleteTemporaryPhoto(path: String?) {
        if (path.isNullOrBlank()) return
        val target = File(path)
        val captureDirectory = File(cacheDirectory, "food_photo_captures").canonicalFile
        val uploadDirectory = File(cacheDirectory, "food_photo_uploads").canonicalFile
        val canonicalTarget = runCatching { target.canonicalFile }.getOrNull() ?: return
        val isManaged = canonicalTarget.parentFile == captureDirectory || canonicalTarget.parentFile == uploadDirectory
        if (isManaged && canonicalTarget.isFile) canonicalTarget.delete()
    }

    fun cleanOldTemporaryPhotos(maxAgeMillis: Long = 24L * 60L * 60L * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        listOf("food_photo_captures", "food_photo_uploads").forEach { directoryName ->
            File(cacheDirectory, directoryName).listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) file.delete()
            }
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > FoodPhotoProcessingLimits.MAX_DIMENSION_PX * 2 ||
            height / sampleSize > FoodPhotoProcessingLimits.MAX_DIMENSION_PX * 2
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun readRotation(source: File): Float {
        val orientation = runCatching {
            ExifInterface(source).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }
}

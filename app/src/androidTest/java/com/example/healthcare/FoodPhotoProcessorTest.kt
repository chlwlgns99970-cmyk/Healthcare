package com.example.healthcare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.healthcare.data.photo.FoodPhotoProcessingLimits
import com.example.healthcare.data.photo.FoodPhotoProcessor
import com.example.healthcare.data.photo.PhotoProcessingResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class FoodPhotoProcessorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val processor = FoodPhotoProcessor(context.cacheDir)

    @After
    fun cleanUp() {
        File(context.cacheDir, "food_photo_captures").deleteRecursively()
        File(context.cacheDir, "food_photo_uploads").deleteRecursively()
    }

    @Test
    fun largeRotatedJpegIsResizedAndExifIsRemoved() = runBlocking {
        val directory = File(context.cacheDir, "food_photo_captures").apply { mkdirs() }
        val source = File(directory, "source.jpg")
        val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(180, 80, 30))
        }
        FileOutputStream(source).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "37/1,0/1,0/1000")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            saveAttributes()
        }

        val result = processor.prepareForUpload(source)

        assertTrue(result is PhotoProcessingResult.Success)
        val output = (result as PhotoProcessingResult.Success).file
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(output.absolutePath, bounds)
        assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= FoodPhotoProcessingLimits.MAX_DIMENSION_PX)
        assertTrue(output.length() <= FoodPhotoProcessingLimits.MAX_UPLOAD_BYTES)
        assertNull(ExifInterface(output).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
    }

    @Test
    fun damagedImageIsRejected() = runBlocking {
        val directory = File(context.cacheDir, "food_photo_captures").apply { mkdirs() }
        val source = File(directory, "damaged.jpg").apply { writeText("not an image") }

        assertTrue(processor.prepareForUpload(source) is PhotoProcessingResult.InvalidImage)
    }

    @Test
    fun managedTemporaryPhotoCanBeDeleted() {
        val directory = File(context.cacheDir, "food_photo_captures").apply { mkdirs() }
        val source = File(directory, "temporary.jpg").apply { writeText("temporary") }

        processor.deleteTemporaryPhoto(source.absolutePath)

        assertTrue(!source.exists())
    }
}

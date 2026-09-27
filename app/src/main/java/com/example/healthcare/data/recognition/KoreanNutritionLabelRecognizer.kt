package com.example.healthcare.data.recognition

import android.content.Context
import android.net.Uri
import com.example.healthcare.domain.NutritionLabelParseResult
import com.example.healthcare.domain.NutritionLabelParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class KoreanNutritionLabelRecognizer(private val context: Context) {
    suspend fun recognize(photoPath: String): NutritionLabelParseResult {
        val image = InputImage.fromFilePath(context, Uri.fromFile(java.io.File(photoPath)))
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        return try {
            val text = suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result.text) }
                    .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
            }
            NutritionLabelParser.parse(text)
        } finally {
            recognizer.close()
        }
    }
}

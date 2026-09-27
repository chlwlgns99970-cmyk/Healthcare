package com.example.healthcare.ui.screens

import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.theme.Ink
import com.example.healthcare.ui.theme.NightBackground
import com.example.healthcare.ui.theme.NightStrong
import com.example.healthcare.ui.theme.NightText
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun FoodCameraScreen(
    onPhotoCaptured: (String) -> Unit,
    onCancel: () -> Unit,
    onCameraUnavailable: () -> Unit,
    onCaptureError: () -> Unit,
    onFileError: () -> Unit = onCaptureError
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val inspectionMode = LocalInspectionMode.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    val cancelled = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            cancelled.set(true)
            cameraProvider?.unbindAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(NightBackground)) {
        if (inspectionMode) {
            Box(
                modifier = Modifier.fillMaxSize().background(NightStrong),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(52.dp), tint = NightText)
                    Text("카메라 미리보기", color = NightText, style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PreviewView(viewContext).also { view ->
                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = view
                        val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                        providerFuture.addListener(
                            {
                                if (cancelled.get()) return@addListener
                                try {
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = view.surfaceProvider
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .setTargetRotation(view.display?.rotation ?: Surface.ROTATION_0)
                                        .build()
                                    provider.unbindAll()
                                    val camera = provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        capture
                                    )
                                    cameraProvider = provider
                                    imageCapture = capture
                                    boundCamera = camera
                                } catch (_: Exception) {
                                    if (!cancelled.get()) onCameraUnavailable()
                                }
                            },
                            ContextCompat.getMainExecutor(viewContext)
                        )
                    }
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues()).padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(color = NightBackground.copy(alpha = 0.78f), shape = MaterialTheme.shapes.extraLarge) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        cancelled.set(true)
                        onCancel()
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "사진 촬영 취소", tint = NightText)
                    }
                    Text("사진으로 기록", color = NightText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (boundCamera?.cameraInfo?.hasFlashUnit() == true) {
                        IconButton(
                            onClick = {
                                flashEnabled = !flashEnabled
                                imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (flashEnabled) Icons.Rounded.Bolt else Icons.Outlined.Bolt,
                                contentDescription = if (flashEnabled) "플래시 끄기" else "플래시 켜기",
                                tint = NightText
                            )
                        }
                    } else {
                        Box(modifier = Modifier.size(48.dp))
                    }
                }
            }

            Surface(
                color = NightBackground.copy(alpha = 0.82f),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "음식 전체가 보이도록 찍어주세요.",
                        color = NightText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("사진을 참고해 음식과 양을 직접 선택할 수 있어요.",
                        color = NightText.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                    Box(
                        modifier = Modifier.size(84.dp).border(3.dp, NightText, CircleShape).padding(7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FilledIconButton(
                            onClick = {
                                val capture = imageCapture ?: return@FilledIconButton
                                if (isCapturing) return@FilledIconButton
                                isCapturing = true
                                capture.targetRotation = previewView?.display?.rotation ?: capture.targetRotation
                                val outputFile = runCatching {
                                    val directory = File(context.cacheDir, "food_photo_captures").apply { mkdirs() }
                                    File.createTempFile("food_capture_", ".jpg", directory)
                                }.getOrElse {
                                    isCapturing = false
                                    if (!cancelled.get()) onFileError()
                                    return@FilledIconButton
                                }
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                                try {
                                    capture.takePicture(
                                        outputOptions,
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                isCapturing = false
                                                when {
                                                    cancelled.get() -> outputFile.delete()
                                                    !outputFile.isFile || outputFile.length() == 0L -> {
                                                        outputFile.delete()
                                                        onFileError()
                                                    }
                                                    else -> onPhotoCaptured(outputFile.absolutePath)
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                outputFile.delete()
                                                isCapturing = false
                                                if (!cancelled.get()) onCaptureError()
                                            }
                                        }
                                    )
                                } catch (_: Exception) {
                                    outputFile.delete()
                                    isCapturing = false
                                    if (!cancelled.get()) onCaptureError()
                                }
                            },
                            enabled = inspectionMode || (imageCapture != null && !isCapturing),
                            modifier = Modifier.fillMaxSize(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = NightText, contentColor = Ink
                            )
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = "음식 사진 촬영", modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@ComposePreview(name = "카메라 390", showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun FoodCameraPreview() {
    HealthCareTheme { FoodCameraScreen({}, {}, {}, {}) }
}

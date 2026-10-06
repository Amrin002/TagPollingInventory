package co.id.lintasarta.tagpollinginventory.camera

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    onImageCaptureCreated: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setFlashMode(flashMode)
            .build()
    }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    LaunchedEffect(cameraSelector) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                onImageCaptureCreated(imageCapture)
            } catch (e: Exception) {
                Log.e("CameraPreview", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}



fun takePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    executor: Executor,
    poleId: String,
    lat: Double,
    lng: Double,
    onPhotoSaved: (File) -> Unit,
    onError: (Exception) -> Unit
) {
    if (imageCapture == null) {
        Log.e("CameraHelper", "ImageCapture is null")
        onError(Exception("ImageCapture is null"))
        return
    }

    val photoDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val photoFile = File(photoDir, "photo_${poleId.replace("-", "_")}_$timeStamp.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                // Post-process the image for watermark and compression
                executor.execute {
                    try {
                        processAndWatermarkPhoto(photoFile, poleId, lat, lng)
                        onPhotoSaved(photoFile)
                    } catch (e: Exception) {
                        Log.e("CameraHelper", "Post-processing failed", e)
                        onError(e)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraHelper", "Photo capture failed: ${exception.message}", exception)
                onError(exception)
            }
        }
    )
}

private fun processAndWatermarkPhoto(file: File, poleId: String, lat: Double, lng: Double) {
    // 1. Read EXIF for rotation
    val exif = ExifInterface(file.absolutePath)
    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.postRotate(180f)
            matrix.preScale(-1f, 1f)
        }
    }

    // 2. Decode and Scale Bitmap (Compression)
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    BitmapFactory.decodeFile(file.absolutePath, options)
    
    // Target max 1920x1080 (or 1080x1920)
    var inSampleSize = 1
    val reqWidth = 1920
    val reqHeight = 1080
    val longest = maxOf(options.outWidth, options.outHeight)
    val shortest = minOf(options.outWidth, options.outHeight)
    
    if (longest > reqWidth || shortest > reqHeight) {
        val halfHeight = longest / 2
        val halfWidth = shortest / 2
        while (halfHeight / inSampleSize >= reqWidth && halfWidth / inSampleSize >= reqHeight) {
            inSampleSize *= 2
        }
    }
    
    options.inJustDecodeBounds = false
    options.inSampleSize = inSampleSize
    val originalBitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return
    
    // Apply rotation
    var rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
    if (rotatedBitmap != originalBitmap) {
        originalBitmap.recycle()
    }
    
    // Ensure mutable bitmap for Canvas
    if (!rotatedBitmap.isMutable) {
        val temp = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        rotatedBitmap.recycle()
        rotatedBitmap = temp
    }

    // 3. Draw Watermark
    val canvas = Canvas(rotatedBitmap)
    
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = (rotatedBitmap.width * 0.035f).coerceAtLeast(24f) // Dynamic text size based on image width
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    
    val bgPaint = Paint().apply {
        color = Color.parseColor("#80000000") // 50% Black
    }
    
    val timeStamp = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US).format(Date())
    val line1 = "Tag Polling Inventory - Lintasarta"
    val line2 = "ID: $poleId"
    val line3 = "Lat: ${String.format(Locale.US, "%.6f", lat)} | Lng: ${String.format(Locale.US, "%.6f", lng)}"
    val line4 = "Time: $timeStamp"
    
    val padding = 20f
    val lineHeight = textPaint.fontSpacing
    
    // Draw background rectangle
    val textWidth1 = textPaint.measureText(line1)
    val textWidth2 = textPaint.measureText(line2)
    val textWidth3 = textPaint.measureText(line3)
    val textWidth4 = textPaint.measureText(line4)
    val maxTextWidth = maxOf(textWidth1, textWidth2, textWidth3, textWidth4)
    
    val rect = Rect(
        padding.toInt(),
        (rotatedBitmap.height - padding - (lineHeight * 4) - padding).toInt(),
        (padding + maxTextWidth + padding * 2).toInt(),
        (rotatedBitmap.height - padding).toInt()
    )
    canvas.drawRect(rect, bgPaint)
    
    // Draw text
    var textY = rotatedBitmap.height - padding - (lineHeight * 3.5f)
    val textX = padding * 2
    canvas.drawText(line1, textX, textY, textPaint)
    textY += lineHeight
    canvas.drawText(line2, textX, textY, textPaint)
    textY += lineHeight
    canvas.drawText(line3, textX, textY, textPaint)
    textY += lineHeight
    canvas.drawText(line4, textX, textY, textPaint)

    // 4. Save Compressed & Watermarked Image
    FileOutputStream(file).use { out ->
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out) // 80% compression
    }
    
    rotatedBitmap.recycle()
}

package co.id.lintasarta.tagpollinginventory.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
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
import androidx.exifinterface.media.ExifInterface
import co.id.lintasarta.tagpollinginventory.data.model.CameraSettings
import co.id.lintasarta.tagpollinginventory.data.model.TimezoneOption
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.math.abs

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
    lat: Double = 0.0,
    lng: Double = 0.0,
    settings: CameraSettings = CameraSettings(),
    onPhotoSaved: (File) -> Unit,
    onError: (Exception) -> Unit
) {
    if (imageCapture == null) {
        Log.e("CameraHelper", "ImageCapture is null")
        onError(Exception("Kamera belum siap, silakan coba lagi."))
        return
    }

    try {
        val photoDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanPoleId = poleId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val photoFile = File(photoDir, "photo_${cleanPoleId}_$timeStamp.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d("CameraHelper", "Photo capture succeeded: ${photoFile.absolutePath}")
                    
                    // Process photo based on user camera settings
                    compressAndWatermarkPhoto(photoFile, poleId, lat, lng, settings)

                    onPhotoSaved(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraHelper", "Photo capture failed: ${exception.message}", exception)
                    onError(exception)
                }
            }
        )
    } catch (e: Exception) {
        Log.e("CameraHelper", "Error in takePhoto: ${e.message}", e)
        onError(e)
    }
}

/**
 * Pemrosesan foto: Full native resolution / resolusi penuh tanpa kompresi secara default,
 * Watermark geotag dinamis (timezone & koordinat), dan EXIF SHA-256 / GPS.
 */
fun compressAndWatermarkPhoto(
    photoFile: File,
    poleCode: String,
    lat: Double,
    lng: Double,
    settings: CameraSettings = CameraSettings()
) {
    try {
        if (!photoFile.exists()) return

        // 1. Read EXIF Orientation
        val exifInitial = ExifInterface(photoFile.absolutePath)
        val orientation = exifInitial.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        // 2. Decode Bitmap
        val rawBitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return

        // 3. Handle rotation
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        val rotatedBitmap = if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) {
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }

        // 4. Resolution handling: Compress/downscale only if isCompressionEnabled is true
        val processedBitmap = if (settings.isCompressionEnabled) {
            val maxDimension = 1920
            val width = rotatedBitmap.width
            val height = rotatedBitmap.height
            if (width > maxDimension || height > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
                val targetW = (width * ratio).toInt()
                val targetH = (height * ratio).toInt()
                Bitmap.createScaledBitmap(rotatedBitmap, targetW, targetH, true)
            } else {
                rotatedBitmap
            }
        } else {
            // Uncompressed: Maintain 100% native resolution
            rotatedBitmap
        }

        // 5. Watermark: Draw if isWatermarkActive is true
        val finalBitmap = if (settings.isWatermarkActive) {
            val watermarked = processedBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(watermarked)
            val w = watermarked.width
            val h = watermarked.height

            val bannerHeight = (h * 0.12f).coerceAtLeast(140f)
            val bannerPaint = Paint().apply {
                color = AndroidColor.argb(190, 0, 0, 0) // Semi-transparent black
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, h - bannerHeight, w.toFloat(), h.toFloat(), bannerPaint)

            val accentBarPaint = Paint().apply {
                color = AndroidColor.parseColor("#1565C0") // Telecom Primary Blue
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, h - bannerHeight, 16f, h.toFloat(), accentBarPaint)

            val textSizeTitle = (bannerHeight * 0.28f).coerceIn(24f, 48f)
            val textSizeBody = (bannerHeight * 0.20f).coerceIn(18f, 34f)

            val titlePaint = Paint().apply {
                color = AndroidColor.WHITE
                textSize = textSizeTitle
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val bodyPaint = Paint().apply {
                color = AndroidColor.parseColor("#E0E0E0")
                textSize = textSizeBody
                isAntiAlias = true
                typeface = Typeface.DEFAULT
            }

            val tagPaint = Paint().apply {
                color = AndroidColor.parseColor("#64B5F6")
                textSize = textSizeBody
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            // Timezone formatting
            val timeZone = if (settings.timezoneOption == TimezoneOption.AUTO) {
                TimeZone.getDefault()
            } else {
                TimeZone.getTimeZone(settings.timezoneOption.zoneId)
            }
            val sdf = SimpleDateFormat("dd MMM yyyy — HH:mm:ss", Locale.US).apply {
                this.timeZone = timeZone
            }
            val tzLabel = if (settings.timezoneOption == TimezoneOption.AUTO) {
                timeZone.getDisplayName(timeZone.inDaylightTime(Date()), TimeZone.SHORT, Locale.US)
            } else {
                settings.timezoneOption.code
            }
            val dateDisplay = "${sdf.format(Date())} $tzLabel"

            // Coordinate formatting
            val coordPrecision = if (settings.isHighAccuracy) "%.6f" else "%.4f"
            val coordText = if (!settings.isGpsActive) {
                "Koor: GPS Nonaktif"
            } else if (lat != 0.0 || lng != 0.0) {
                "Koor: ${String.format(Locale.US, coordPrecision, lat)}, ${String.format(Locale.US, coordPrecision, lng)}"
            } else {
                "Koor: Menunggu GPS Lock"
            }

            val startX = 36f
            val line1Y = h - bannerHeight + (bannerHeight * 0.32f)
            val line2Y = h - bannerHeight + (bannerHeight * 0.62f)
            val line3Y = h - bannerHeight + (bannerHeight * 0.88f)

            canvas.drawText("TIANG: $poleCode", startX, line1Y, titlePaint)
            canvas.drawText("$coordText  •  Waktu: $dateDisplay", startX, line2Y, bodyPaint)
            canvas.drawText("LINTASARTA RNIMS FIELD SURVEY (MPO AS-BUILT) • ISO-27001", startX, line3Y, tagPaint)

            watermarked
        } else {
            processedBitmap
        }

        // 6. Save JPEG: Quality 80% if compression enabled, otherwise 100% full quality
        val quality = if (settings.isCompressionEnabled) 80 else 100
        FileOutputStream(photoFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.flush()
        }

        // Clean up bitmaps
        if (rotatedBitmap != rawBitmap) rawBitmap.recycle()
        if (processedBitmap != rotatedBitmap) rotatedBitmap.recycle()
        if (finalBitmap != processedBitmap) {
            processedBitmap.recycle()
            finalBitmap.recycle()
        } else if (finalBitmap != rotatedBitmap && finalBitmap != rawBitmap) {
            finalBitmap.recycle()
        }

        // 7. Write EXIF GPS & SHA-256 if isExifSaveActive is true
        if (settings.isExifSaveActive) {
            if (settings.isGpsActive && (lat != 0.0 || lng != 0.0)) {
                writeExifGps(photoFile, lat, lng)
            } else {
                writeSha256Exif(photoFile)
            }
        }

        Log.d("CameraHelper", "Photo saved: ${photoFile.length() / 1024} KB (Compressed: ${settings.isCompressionEnabled}, Watermark: ${settings.isWatermarkActive}, Quality: $quality%)")
    } catch (e: Exception) {
        Log.e("CameraHelper", "Failed to process photo: ${e.message}", e)
    }
}

fun writeExifGps(photoFile: File, lat: Double, lng: Double) {
    try {
        val exif = ExifInterface(photoFile.absolutePath)

        val latRef = if (lat >= 0) "N" else "S"
        val lngRef = if (lng >= 0) "E" else "W"
        val absLat = abs(lat)
        val absLng = abs(lng)

        val latDegrees = absLat.toInt()
        val latMinutes = ((absLat - latDegrees) * 60).toInt()
        val latSeconds = (absLat - latDegrees - latMinutes / 60.0) * 3600

        val lngDegrees = absLng.toInt()
        val lngMinutes = ((absLng - lngDegrees) * 60).toInt()
        val lngSeconds = (absLng - lngDegrees - lngMinutes / 60.0) * 3600

        val latFormatted = "$latDegrees/1,$latMinutes/1,${(latSeconds * 1000).toInt()}/1000"
        val lngFormatted = "$lngDegrees/1,$lngMinutes/1,${(lngSeconds * 1000).toInt()}/1000"

        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latFormatted)
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lngFormatted)
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lngRef)

        val sha256 = calculateFileSha256(photoFile)
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "SHA256:$sha256")

        exif.saveAttributes()
        Log.d("CameraHelper", "EXIF GPS & SHA-256 successfully written: $sha256")
    } catch (e: Exception) {
        Log.e("CameraHelper", "Failed to write EXIF GPS: ${e.message}", e)
    }
}

fun writeSha256Exif(photoFile: File) {
    try {
        val exif = ExifInterface(photoFile.absolutePath)
        val sha256 = calculateFileSha256(photoFile)
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "SHA256:$sha256")
        exif.saveAttributes()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun calculateFileSha256(file: File): String {
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        ""
    }
}

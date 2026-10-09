package co.id.lintasarta.tagpollinginventory.camera

import android.content.Context
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
import java.io.File
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
                    
                    // Write EXIF GPS Location and SHA-256 digest
                    if (lat != 0.0 || lng != 0.0) {
                        writeExifGps(photoFile, lat, lng)
                    } else {
                        writeSha256Exif(photoFile)
                    }

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


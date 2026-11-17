package com.example.data.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object MediaUtils {

    suspend fun encodeImageUriToDataUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 720,
        quality: Int = 72
    ): String? = withContext(Dispatchers.IO) {
        try {
            val inputBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, bounds)

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDimension * 2 ||
                bounds.outHeight / sampleSize > maxDimension * 2
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, decodeOpts)
                ?: return@withContext null

            val scale = minOf(
                maxDimension.toFloat() / decoded.width.coerceAtLeast(1),
                maxDimension.toFloat() / decoded.height.coerceAtLeast(1),
                1f
            )
            val targetBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }

            val out = ByteArrayOutputStream()
            targetBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (targetBitmap !== decoded) decoded.recycle()
            targetBitmap.recycle()

            val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (_: Exception) {
            null
        }
    }

    suspend fun copyVideoUriToAppStorage(
        context: Context,
        uri: Uri
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val videosDir = File(context.filesDir, "starmax_videos").apply { mkdirs() }
            val outFile = File(videosDir, "vid_${UUID.randomUUID()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val thumbDataUri = extractVideoThumbnailDataUri(outFile.absolutePath).orEmpty()
            Uri.fromFile(outFile).toString() to thumbDataUri
        } catch (_: Exception) {
            null
        }
    }

    private fun extractVideoThumbnailDataUri(filePath: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val scale = minOf(540f / frame.width.coerceAtLeast(1), 540f / frame.height.coerceAtLeast(1), 1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt().coerceAtLeast(1),
                    (frame.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                frame
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            if (scaled !== frame) frame.recycle()
            scaled.recycle()
            "data:image/jpeg;base64,${Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)}"
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun decodeBase64DataUri(dataUri: String): ByteArray? {
        if (!dataUri.startsWith("data:image")) return null
        val commaIdx = dataUri.indexOf(',')
        if (commaIdx <= 0 || commaIdx + 1 >= dataUri.length) return null
        return try {
            Base64.decode(dataUri.substring(commaIdx + 1), Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }
}

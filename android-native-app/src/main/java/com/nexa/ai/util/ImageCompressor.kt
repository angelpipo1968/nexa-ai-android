package com.nexa.ai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageCompressor {

    /**
     * Convierte URI a Base64 con compresión y redimensionamiento
     */
    fun uriToBase64(
        context: Context,
        uri: Uri,
        maxWidth: Int = 1024,
        quality: Int = 80
    ): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("No se pudo abrir la imagen")

        // Primera pasada: decodificar solo dimensiones
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        // Calcular inSampleSize
        val options2 = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(options, maxWidth, maxWidth)
        }

        // Segunda pasada: decodificar completo
        val inputStream2 = context.contentResolver.openInputStream(uri)
            ?: throw Exception("No se pudo abrir la imagen")
        val bitmap = BitmapFactory.decodeStream(inputStream2, null, options2)
        inputStream2.close()

        // Redimensionar si es necesario
        val resized = resizeBitmap(bitmap, maxWidth)

        return bitmapToBase64(resized, quality)
    }

    fun bitmapToBase64(bitmap: Bitmap?, quality: Int = 80): String {
        val outputStream = ByteArrayOutputStream()
        bitmap?.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Extrae un frame de un video en un timestamp específico
     */
    fun extractVideoFrame(
        context: Context,
        videoUri: Uri,
        timestampUs: Long = 0
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            retriever.getFrameAtTime(timestampUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Extrae múltiples frames de un video para análisis completo
     */
    fun extractVideoFrames(
        context: Context,
        videoUri: Uri,
        numFrames: Int = 5
    ): List<Pair<Long, Bitmap>> {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Pair<Long, Bitmap>>()

        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val interval = if (numFrames > 1) durationMs / (numFrames + 1) else 0L

            for (i in 1..numFrames) {
                val timestampUs = (interval * i) * 1000 // convertir a microsegundos
                val frame = retriever.getFrameAtTime(
                    timestampUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                frame?.let { frames.add(Pair(interval * i, it)) }
            }
        } catch (e: Exception) {
            // Log error
        } finally {
            retriever.release()
        }

        return frames
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                   (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun resizeBitmap(bitmap: Bitmap?, maxWidth: Int): Bitmap? {
        bitmap ?: return null
        val ratio = maxWidth.toFloat() / bitmap.width.coerceAtLeast(1)
        if (ratio >= 1f) return bitmap

        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}

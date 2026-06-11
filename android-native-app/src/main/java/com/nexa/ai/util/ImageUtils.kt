package com.nexa.ai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {
    
    fun uriToBase64(context: Context, uri: Uri, maxWidth: Int = 1024): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        
        // Primera lectura: solo dimensiones
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        // Calcular escala
        var scale = 1
        if (options.outWidth > maxWidth || options.outHeight > maxWidth) {
            val halfWidth = options.outWidth / 2
            val halfHeight = options.outHeight / 2
            while ((halfWidth / scale) >= maxWidth && (halfHeight / scale) >= maxWidth) {
                scale *= 2
            }
        }

        // Segunda lectura: bitmap real
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
        val inputStream2 = context.contentResolver.openInputStream(uri) ?: return ""
        val bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
        inputStream2.close()

        // Comprimir a JPEG
        val outputStream = ByteArrayOutputStream()
        bitmap?.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

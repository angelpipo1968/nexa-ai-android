package com.nexa.ai.util

import android.app.Application
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nexa.ai.ui.NexaStrings
import com.nexa.ai.viewmodel.AppLanguage
import com.nexa.ai.viewmodel.Message
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a PDF export of a chat message.
 * Extracted from ViewModel for separation of concerns.
 */
object PdfExporter {

    fun exportToPdf(application: Application, message: Message, language: AppLanguage) {
        try {
            val content = message.content.trim()
            if (content.isEmpty()) {
                Toast.makeText(application, NexaStrings.get("nothing_to_export", language), Toast.LENGTH_SHORT).show()
                return
            }

            val pdfDocument = PdfDocument()
            val paint = Paint().apply { isAntiAlias = true }
            val pageWidth = 595
            val pageHeight = 842
            val marginLeft = 50f
            val maxTextWidth = 495f
            val maxY = 790f
            val lineHeight = 18f
            val paragraphGap = 4f

            var pageNum = 0
            var page: PdfDocument.Page? = null
            var canvas: android.graphics.Canvas? = null
            var y: Float

            fun newPage(startY: Float = 50f): Float {
                if (pageNum > 0) pdfDocument.finishPage(page!!)
                pageNum++
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                page = pdfDocument.startPage(info)
                canvas = page!!.canvas
                paint.textSize = 12f
                paint.isFakeBoldText = false
                paint.color = Color.BLACK
                return startY
            }

            fun ensureSpace(currentY: Float, needed: Float = lineHeight): Float {
                return if (currentY + needed > maxY) newPage() else currentY
            }

            // First page — header
            y = newPage(95f)

            paint.textSize = 16f
            paint.isFakeBoldText = true
            paint.color = Color.parseColor("#00E5A0")
            canvas!!.drawText("NEXA PRO", marginLeft, 45f, paint)

            paint.textSize = 10f
            paint.isFakeBoldText = false
            paint.color = Color.GRAY
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            canvas!!.drawText(dateStr, marginLeft, 62f, paint)

            paint.color = Color.parseColor("#00E5A0")
            paint.strokeWidth = 1f
            canvas!!.drawLine(marginLeft, 72f, 545f, 72f, paint)

            paint.textSize = 12f
            paint.isFakeBoldText = false
            paint.color = Color.BLACK

            // Content
            for (line in content.split("\n")) {
                y = ensureSpace(y)
                val words = line.split(" ")
                var currentLine = ""
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(testLine) > maxTextWidth) {
                        y = ensureSpace(y)
                        canvas!!.drawText(currentLine, marginLeft, y, paint)
                        y += lineHeight
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }
                if (currentLine.isNotEmpty()) {
                    y = ensureSpace(y)
                    canvas!!.drawText(currentLine, marginLeft, y, paint)
                    y += lineHeight
                }
                y += paragraphGap
            }

            // Footer
            paint.textSize = 8f
            paint.color = Color.LTGRAY
            canvas!!.drawText(NexaStrings.get("generated_by", language), marginLeft, 820f, paint)

            pdfDocument.finishPage(page!!)

            val fileName = "nexa_export_${System.currentTimeMillis()}.pdf"
            val file = File(application.cacheDir, fileName)
            FileOutputStream(file).use { fos -> pdfDocument.writeTo(fos) }
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(
                application, "${application.packageName}.fileprovider", file
            )

            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val shareIntent = android.content.Intent.createChooser(intent, NexaStrings.get("export_pdf_title", language))
            shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            application.startActivity(shareIntent)

        } catch (e: Exception) {
            android.util.Log.e("NEXA", "PDF Error: ${e.message}", e)
            Toast.makeText(application, "Error al generar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

package com.nexa.ai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Simple markdown renderer for chat messages.
 * Supports: **bold**, *italic*, `code`, ```code blocks```, - lists, ### headers
 */
@Composable
fun rememberMarkdownText(content: String): AnnotatedString {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val accent = Color(0xFF00E5A0)

    return remember(content, onSurface) {
        buildAnnotatedString {
            val lines = content.split("\n")
            var inCodeBlock = false
            val codeBlockContent = StringBuilder()

            for ((index, line) in lines.withIndex()) {
                // Code block toggle
                if (line.trimStart().startsWith("```")) {
                    if (inCodeBlock) {
                        // End code block
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            background = codeBg,
                            color = onSurface.copy(alpha = 0.85f)
                        )) {
                            append(codeBlockContent.toString().trimEnd())
                        }
                        codeBlockContent.clear()
                        inCodeBlock = false
                    } else {
                        inCodeBlock = true
                    }
                    if (index < lines.size - 1) append("\n")
                    continue
                }

                if (inCodeBlock) {
                    codeBlockContent.append(line)
                    if (index < lines.size - 1) codeBlockContent.append("\n")
                    continue
                }

                // Headers
                if (line.startsWith("### ")) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = onSurface)) {
                        append(line.removePrefix("### "))
                    }
                } else if (line.startsWith("## ")) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = onSurface)) {
                        append(line.removePrefix("## "))
                    }
                } else if (line.startsWith("# ")) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = onSurface)) {
                        append(line.removePrefix("# "))
                    }
                }
                // Unordered list
                else if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
                    append("  •  ")
                    appendInlineMarkdown(line.trimStart().removePrefix("- ").removePrefix("* "), onSurface, accent)
                }
                // Ordered list
                else if (line.trimStart().matches(Regex("^\\d+\\.\\s.*"))) {
                    val num = line.trimStart().substringBefore(".")
                    append("  $num.  ")
                    appendInlineMarkdown(line.trimStart().substringAfter(". "), onSurface, accent)
                }
                // Regular line
                else {
                    appendInlineMarkdown(line, onSurface, accent)
                }

                if (index < lines.size - 1) append("\n")
            }

            // Unclosed code block
            if (inCodeBlock && codeBlockContent.isNotEmpty()) {
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    background = codeBg,
                    color = onSurface.copy(alpha = 0.85f)
                )) {
                    append(codeBlockContent.toString().trimEnd())
                }
            }
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineMarkdown(
    text: String, onSurface: Color, accent: Color
) {
    var i = 0
    while (i < text.length) {
        // Inline code `...`
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end != -1) {
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    background = onSurface.copy(alpha = 0.08f),
                    color = accent.copy(alpha = 0.8f)
                )) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }

        // Bold **...**
        if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = onSurface)) {
                    append(text.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }

        // Italic *...*
        if (text[i] == '*' && (i + 1 < text.length && text[i + 1] != '*')) {
            val end = text.indexOf('*', i + 1)
            if (end != -1 && end > i + 1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = onSurface.copy(alpha = 0.85f))) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }

        // Strikethrough ~~...~~
        if (i + 1 < text.length && text[i] == '~' && text[i + 1] == '~') {
            val end = text.indexOf("~~", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(color = onSurface.copy(alpha = 0.4f))) {
                    append(text.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }

        append(text[i])
        i++
    }
}

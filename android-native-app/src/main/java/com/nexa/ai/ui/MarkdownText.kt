package com.nexa.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.nexa.ai.R

/**
 * Simple markdown renderer for chat messages.
 * Supports: **bold**, *italic*, `code`, ```code blocks```, - lists, ### headers,
 * [links](url), and plain URLs.
 * v2: Added clickable blue links with UrlAnnotation
 */

// Tag for link annotations
private const val LINK_TAG = "URL"

@Composable
fun rememberMarkdownText(content: String): AnnotatedString {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val accent = Color(0xFF00E5A0)
    // Blue link color - works on both dark and light themes
    val linkColor = Color(0xFF4DA8FF)

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
                        appendInlineMarkdown(line.removePrefix("### "), onSurface, accent, linkColor)
                    }
                } else if (line.startsWith("## ")) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = onSurface)) {
                        appendInlineMarkdown(line.removePrefix("## "), onSurface, accent, linkColor)
                    }
                } else if (line.startsWith("# ")) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = onSurface)) {
                        appendInlineMarkdown(line.removePrefix("# "), onSurface, accent, linkColor)
                    }
                }
                // Table row (basic support - just render as text with formatting)
                else if (line.trimStart().startsWith("|") && line.trimEnd().endsWith("|")) {
                    appendInlineMarkdown(line, onSurface, accent, linkColor)
                }
                // Unordered list
                else if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
                    append("  •  ")
                    appendInlineMarkdown(line.trimStart().removePrefix("- ").removePrefix("* "), onSurface, accent, linkColor)
                }
                // Ordered list
                else if (line.trimStart().matches(Regex("^\\d+\\.\\s.*"))) {
                    val num = line.trimStart().substringBefore(".")
                    append("  $num.  ")
                    appendInlineMarkdown(line.trimStart().substringAfter(". "), onSurface, accent, linkColor)
                }
                // Regular line
                else {
                    appendInlineMarkdown(line, onSurface, accent, linkColor)
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
    text: String, onSurface: Color, accent: Color, linkColor: Color
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

        // Markdown link [text](url) — MUST be checked before bold/italic since [ can follow **
        if (text[i] == '[') {
            // Find the matching ]
            val textEnd = text.indexOf(']', i + 1)
            if (textEnd != -1 && textEnd + 1 < text.length && text[textEnd + 1] == '(') {
                // Find the matching )
                val urlEnd = text.indexOf(')', textEnd + 2)
                if (urlEnd != -1) {
                    val linkText = text.substring(i + 1, textEnd)
                    val linkUrl = text.substring(textEnd + 2, urlEnd)
                    
                    // Add clickable link with blue styling
                    pushStringAnnotation(tag = LINK_TAG, annotation = linkUrl)
                    withStyle(SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.None
                    )) {
                        append(linkText)
                    }
                    pop()
                    
                    i = urlEnd + 1
                    continue
                }
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

        // Plain URL detection: http:// or https://
        if (text.startsWith("http://", i) || text.startsWith("https://", i)) {
            // Find end of URL (space, newline, or end of text)
            val urlEnd = findUrlEnd(text, i)
            val url = text.substring(i, urlEnd)
            
            // Create a readable display text from the URL
            val displayText = simplifyUrl(url)
            
            pushStringAnnotation(tag = LINK_TAG, annotation = url)
            withStyle(SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.None
            )) {
                append(displayText)
            }
            pop()
            
            i = urlEnd
            continue
        }

        append(text[i])
        i++
    }
}

/**
 * Find the end of a URL in text starting at position start
 */
private fun findUrlEnd(text: String, start: Int): Int {
    var end = start
    while (end < text.length) {
        val c = text[end]
        // URL ends at whitespace, certain punctuation, or end of text
        if (c.isWhitespace() || c == ')' || c == ']' || c == '}' || c == '>') {
            break
        }
        end++
    }
    return end
}

/**
 * Simplify a URL for display purposes
 */
private fun simplifyUrl(url: String): String {
    return try {
        val uri = Uri.parse(url)
        val host = uri.host ?: url
        val path = uri.path
        if (path != null && path.length > 1) {
            "$host${path.take(30)}${if (path.length > 30) "..." else ""}"
        } else {
            host
        }
    } catch (e: Exception) {
        url.take(40)
    }
}

/**
 * Composable that renders markdown text with clickable links.
 * Replaces the plain Text() composable for markdown content.
 */
@Composable
fun MarkdownClickableText(
    markdownText: AnnotatedString,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 22.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val context = LocalContext.current
    
    ClickableText(
        text = markdownText,
        style = androidx.compose.ui.text.TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color
        ),
        onTextLayout = onTextLayout,
        onClick = { offset ->
            // Check if the click is on a link annotation
            markdownText.getStringAnnotations(
                tag = LINK_TAG,
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                // Open the URL in the browser
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback: try to open with share intent
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, annotation.item)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val title = context.getString(R.string.open_link)
                        context.startActivity(Intent.createChooser(shareIntent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {}
                }
            }
        }
    )
}

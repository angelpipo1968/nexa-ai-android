package com.nexa.ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.data.UpdateInfo
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.viewmodel.*

// ═══════════════════════════════════════
//  MESSAGES
// ═══════════════════════════════════════

@Composable
fun ChatMessages(messages: List<Message>, isThinking: Boolean, language: AppLanguage,
    speakingMessageId: String?, onSpeakMessage: (String, String) -> Unit,
    onCopyMessage: (String) -> Unit, onExportMessage: (Message) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LazyColumn(modifier = modifier.fillMaxWidth(), state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (messages.isEmpty()) item { EmptyState(language) }
        items(messages, key = { it.id }) { msg ->
            MessageBubble(message = msg, isSpeaking = speakingMessageId == msg.id, language = language,
                onSpeak = { onSpeakMessage(msg.content, msg.id) }, onCopy = { onCopyMessage(msg.content) },
                onExport = { onExportMessage(msg) })
        }
        if (isThinking) item { ThinkingIndicator(language) }
    }
}

@Composable
fun EmptyState(lang: AppLanguage) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 80.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val infiniteTransition = rememberInfiniteTransition(label = "empty")
        val glowScale by infiniteTransition.animateFloat(initialValue = 0.95f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(animation = tween(4000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "pulse")
        val glowAlpha by infiniteTransition.animateFloat(initialValue = 0.12f, targetValue = 0.25f,
            animationSpec = infiniteRepeatable(animation = tween(3000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "glowAlpha")
        Box(modifier = Modifier.size((64 * glowScale).dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.radialGradient(listOf(NexaAccent.copy(alpha = glowAlpha), NexaAccent.copy(alpha = 0.02f), Color.Transparent))),
            contentAlignment = Alignment.Center) { Text("⚡", fontSize = 32.sp) }
    }
}

@Composable
fun MessageBubble(message: Message, isSpeaking: Boolean, language: AppLanguage,
    onSpeak: () -> Unit, onCopy: () -> Unit, onExport: () -> Unit) {
    val isUser = message.role == "user"
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (isUser) 20.dp else 6.dp, bottomEnd = if (isUser) 6.dp else 20.dp),
            color = if (isUser) NexaAccent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = if (!isUser) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)) else null) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser && !message.isStreaming && message.content.isNotEmpty()) {
                    Row(modifier = Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(NexaAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center) { Text("⚡", fontSize = 6.sp) }
                        Text("NEXA", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = NexaAccent.copy(alpha = 0.45f), letterSpacing = 1.5.sp)
                    }
                }
                if (message.attachmentName != null && message.content.startsWith("📎")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = NexaAccent.copy(alpha = 0.15f), modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Attachment, null, modifier = Modifier.size(14.dp), tint = NexaAccent) }
                        }
                        Text(message.attachmentName, fontSize = 12.sp, color = NexaAccent, fontWeight = FontWeight.Medium)
                    }
                    if (message.content.length > message.attachmentName.length + 3) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(message.content.removePrefix("📎 ${message.attachmentName}\n"), fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                } else if (message.isStreaming && message.content.isEmpty()) {
                    DotsTyping()
                } else {
                    Text(text = message.content, fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (!isUser && !message.isStreaming && message.content.isNotEmpty()) {
            Row(modifier = Modifier.padding(top = 6.dp, start = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                // Speak button
                Surface(onClick = onSpeak, shape = RoundedCornerShape(8.dp),
                    color = if (isSpeaking) NexaAccent.copy(alpha = 0.12f) else Color.Transparent,
                    modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp, null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSpeaking) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                // Copy button
                Surface(onClick = onCopy, shape = RoundedCornerShape(8.dp), color = Color.Transparent, modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
                // More menu
                var showMsgMenu by remember { mutableStateOf(false) }
                Box {
                    Surface(onClick = { showMsgMenu = true }, shape = RoundedCornerShape(8.dp), color = Color.Transparent, modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                    DropdownMenu(expanded = showMsgMenu, onDismissRequest = { showMsgMenu = false }) {
                        DropdownMenuItem(
                            text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp)); Text(NexaStrings.get("copy", language)) } },
                            onClick = { showMsgMenu = false; onCopy() }
                        )
                        DropdownMenuItem(
                            text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(18.dp)); Text(NexaStrings.get("read_aloud", language)) } },
                            onClick = { showMsgMenu = false; onSpeak() }
                        )
                        DropdownMenuItem(
                            text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp)); Text(NexaStrings.get("export_pdf", language)) } },
                            onClick = { showMsgMenu = false; onExport() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingIndicator(lang: AppLanguage) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { index ->
                val infiniteTransition = rememberInfiniteTransition(label = "dot$index")
                val alpha by infiniteTransition.animateFloat(initialValue = 0.15f, targetValue = 0.8f, animationSpec = infiniteRepeatable(animation = tween(700, delayMillis = index * 180), repeatMode = RepeatMode.Reverse), label = "dotAlpha$index")
                val size by infiniteTransition.animateFloat(initialValue = 5f, targetValue = 7f, animationSpec = infiniteRepeatable(animation = tween(700, delayMillis = index * 180), repeatMode = RepeatMode.Reverse), label = "dotSize$index")
                Box(modifier = Modifier.size(size.dp).clip(CircleShape).background(NexaAccent.copy(alpha = alpha)))
            }
        }
        Text(NexaStrings.get("thinking", lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), letterSpacing = 0.5.sp)
    }
}

@Composable
fun DotsTyping() {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "typing$index")
            val alpha by infiniteTransition.animateFloat(initialValue = 0.15f, targetValue = 0.7f, animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = index * 150), repeatMode = RepeatMode.Reverse), label = "typingAlpha$index")
            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(NexaAccent.copy(alpha = alpha)))
        }
    }
}

// ═══════════════════════════════════════
//  INPUT BAR
// ═══════════════════════════════════════

@Composable
fun InputBar(text: String, language: AppLanguage, isListening: Boolean, isSpeaking: Boolean,
    pendingAttachment: String?, onTextChange: (String) -> Unit, onSend: () -> Unit,
    onStartListening: () -> Unit, onStopListening: () -> Unit, onStopSpeaking: () -> Unit,
    onAttachFile: () -> Unit, onClearAttachment: () -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var showMenu by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
        shadowElevation = 0.dp, border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Attachment preview
            AnimatedVisibility(visible = pendingAttachment != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = NexaAccent.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = NexaAccent.copy(alpha = 0.15f), modifier = Modifier.size(32.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Attachment, null, modifier = Modifier.size(16.dp), tint = NexaAccent) }
                        }
                        Text(pendingAttachment ?: "", fontSize = 13.sp, color = NexaAccent, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = onClearAttachment, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Main input row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Attach menu
                Box {
                    Surface(onClick = { showMenu = true }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)), modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp)) }
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Photo, null, modifier = Modifier.size(20.dp), tint = NexaAccent); Text(NexaStrings.get("upload_photo", language), fontSize = 14.sp) } }, onClick = { showMenu = false; onAttachFile() })
                        DropdownMenuItem(text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(20.dp), tint = NexaAccent); Text(NexaStrings.get("upload_pdf", language), fontSize = 14.sp) } }, onClick = { showMenu = false; onAttachFile() })
                    }
                }

                // Text input
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)), modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(value = text, onValueChange = onTextChange,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 42.dp),
                            placeholder = { Text(if (isListening) NexaStrings.get("listening", language) else NexaStrings.get("input_hint", language), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f), fontSize = 14.sp, letterSpacing = 0.3.sp) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend(); keyboardController?.hide() }),
                            maxLines = 4, textStyle = LocalTextStyle.current.copy(fontSize = 15.sp))
                        Surface(onClick = { if (isListening) onStopListening() else onStartListening() }, shape = CircleShape,
                            color = if (isListening) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else Color.Transparent, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(if (isListening) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, tint = if (isListening) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else NexaAccent.copy(alpha = 0.5f), modifier = Modifier.size(18.dp)) }
                        }
                    }
                }

                // Send button
                val canSend = text.isNotBlank() || pendingAttachment != null
                Surface(onClick = { onSend(); keyboardController?.hide() }, enabled = canSend, shape = CircleShape,
                    color = if (canSend) NexaAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = if (canSend) BorderStroke(1.dp, NexaAccent.copy(alpha = 0.3f)) else null, modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = NexaStrings.get("send", language), tint = if (canSend) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(18.dp)) }
                }
            }

            // Hint
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                Text(NexaStrings.get("mic_hint", language), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), letterSpacing = 0.8.sp)
            }
        }
    }
}

// ═══════════════════════════════════════
//  UPDATE DIALOG
// ═══════════════════════════════════════

@Composable
fun UpdateDialog(updateInfo: UpdateInfo, onDismiss: () -> Unit, onUpdate: () -> Unit, language: AppLanguage = AppLanguage.SPANISH) {
    AlertDialog(onDismissRequest = { if (!updateInfo.forceUpdate) onDismiss() }, containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(NexaAccent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center) { Text("🔄", fontSize = 14.sp) }
                Text(NexaStrings.get("update_available", language), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
            }
        },
        text = {
            Column {
                Text("v${updateInfo.versionName}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NexaAccent.copy(alpha = 0.7f), letterSpacing = 0.5.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(updateInfo.changelog, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), lineHeight = 20.sp)
            }
        },
        confirmButton = { Button(onClick = onUpdate, colors = ButtonDefaults.buttonColors(containerColor = NexaAccent), shape = RoundedCornerShape(12.dp)) { Text(NexaStrings.get("update_now", language), color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) } },
        dismissButton = { if (!updateInfo.forceUpdate) TextButton(onClick = onDismiss) { Text(NexaStrings.get("later", language), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) } },
        shape = RoundedCornerShape(24.dp))
}



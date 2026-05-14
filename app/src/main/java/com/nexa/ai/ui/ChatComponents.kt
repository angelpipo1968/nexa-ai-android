package com.nexa.ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.nexa.ai.ui.theme.NexaUserBubbleDark
import com.nexa.ai.ui.theme.NexaUserBubbleLight
import com.nexa.ai.ui.theme.dynamicPrimaryColor
import com.nexa.ai.ui.theme.supportsDynamicColors
import com.nexa.ai.viewmodel.*

// ═══════════════════════════════════════
//  MESSAGES
// ═══════════════════════════════════════

@Composable
fun ChatMessages(messages: List<Message>, isThinking: Boolean, language: AppLanguage,
    speakingMessageId: String?, onSpeakMessage: (String, String) -> Unit,
    onCopyMessage: (String) -> Unit, onExportMessage: (Message) -> Unit,
    onRegenerate: () -> Unit = {}, isDarkTheme: Boolean = true,
    themeMode: ThemeMode = ThemeMode.DARK, modifier: Modifier = Modifier,
    onClearChat: () -> Unit = {}, onStopSpeaking: () -> Unit = {},
    isSpeaking: Boolean = false) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LazyColumn(modifier = modifier.fillMaxWidth(), state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (messages.isEmpty()) item { EmptyState(language) }
        items(messages, key = { it.id }) { msg ->
            val isLast = msg == messages.lastOrNull()
            val isLastAssistant = isLast && msg.role == "assistant" && !msg.isStreaming && msg.content.isNotEmpty()
            MessageBubble(message = msg, isSpeaking = speakingMessageId == msg.id, language = language,
                isDarkTheme = isDarkTheme, themeMode = themeMode,
                onSpeak = { onSpeakMessage(msg.content, msg.id) }, onCopy = { onCopyMessage(msg.content) },
                onExport = { onExportMessage(msg) }, onRegenerate = if (isLastAssistant) onRegenerate else null,
                isLastAssistant = isLastAssistant, onClearChat = onClearChat,
                onStopSpeaking = onStopSpeaking, isGloballySpeaking = isSpeaking)
        }
        if (isThinking && messages.isEmpty()) item { ShimmerLoading(isDarkTheme = isDarkTheme) }
        if (isThinking && messages.isNotEmpty()) item { ThinkingIndicator(language) }
    }
}

@Composable
fun EmptyState(lang: AppLanguage) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 120.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Premium pulsating glow with layered effects
        val infiniteTransition = rememberInfiniteTransition(label = "empty")
        val glowScale by infiniteTransition.animateFloat(
            initialValue = 0.88f, targetValue = 1.12f,
            animationSpec = infiniteRepeatable(animation = tween(5000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "pulse"
        )
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.06f, targetValue = 0.2f,
            animationSpec = infiniteRepeatable(animation = tween(4000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "glowAlpha"
        )
        val outerGlowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.02f, targetValue = 0.08f,
            animationSpec = infiniteRepeatable(animation = tween(6000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "outerGlow"
        )
        val rotation by infiniteTransition.animateFloat(
            initialValue = -2f, targetValue = 2f,
            animationSpec = infiniteRepeatable(animation = tween(7000, easing = EaseInOut), repeatMode = RepeatMode.Reverse),
            label = "wobble"
        )

        Box(contentAlignment = Alignment.Center) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size((80 * glowScale).dp)
                    .graphicsLayer {
                        alpha = outerGlowAlpha
                        rotationZ = rotation
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.radialGradient(listOf(
                            NexaAccent.copy(alpha = 0.15f),
                            NexaAccent.copy(alpha = 0.03f),
                            Color.Transparent
                        ))
                    )
            )
            // Inner glow
            Box(
                modifier = Modifier
                    .size((56 * glowScale).dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.radialGradient(listOf(
                        NexaAccent.copy(alpha = glowAlpha),
                        NexaAccent.copy(alpha = 0.02f),
                        Color.Transparent
                    ))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "⚡",
                    fontSize = 26.sp,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = rotation * 0.3f
                    }
                )
            }
        }

        // Minimal brand text
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("NEXA", fontSize = 14.sp, fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), letterSpacing = 6.sp)
            Box(modifier = Modifier.width(24.dp).height(0.5.dp).background(NexaAccent.copy(alpha = 0.15f)))
        }

        // Welcome message
        Text(
            NexaStrings.get("welcome_msg", lang),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            textAlign = TextAlign.Center,
            letterSpacing = 0.3.sp,
            lineHeight = 20.sp
        )

        // Voice activation hint (below welcome text)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp)
        ) {
            // Mic icon with pulse
            val pulseTransition = rememberInfiniteTransition(label = "micPulse")
            val micScale by pulseTransition.animateFloat(
                initialValue = 0.95f, targetValue = 1.05f,
                animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOut), RepeatMode.Reverse),
                label = "micScale"
            )
            val micGlow by pulseTransition.animateFloat(
                initialValue = 0.08f, targetValue = 0.18f,
                animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOut), RepeatMode.Reverse),
                label = "micGlow"
            )
            Box(
                modifier = Modifier
                    .size((36 * micScale).dp)
                    .clip(CircleShape)
                    .background(NexaAccent.copy(alpha = micGlow)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = NexaAccent.copy(alpha = 0.6f)
                )
            }
            Text(
                if (lang == AppLanguage.SPANISH) "Toca para activar voz" else "Tap to activate voice",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                letterSpacing = 0.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun MessageBubble(message: Message, isSpeaking: Boolean, language: AppLanguage,
    isDarkTheme: Boolean = true, themeMode: ThemeMode = ThemeMode.DARK,
    onSpeak: () -> Unit, onCopy: () -> Unit, onExport: () -> Unit, onRegenerate: (() -> Unit)? = null,
    isLastAssistant: Boolean = false, onClearChat: () -> Unit = {},
    onStopSpeaking: () -> Unit = {}, isGloballySpeaking: Boolean = false) {
    val isUser = message.role == "user"

    // Dynamic color for user bubble: SYSTEM mode uses Material You, others use custom colors
    val userBubbleColor = when (themeMode) {
        ThemeMode.SYSTEM -> {
            if (isDarkTheme) dynamicPrimaryColor().copy(alpha = 0.15f)
            else dynamicPrimaryColor().copy(alpha = 0.85f)
        }
        ThemeMode.DARK -> NexaUserBubbleDark
        ThemeMode.LIGHT -> NexaUserBubbleLight
    }
    val userTextColor = when (themeMode) {
        ThemeMode.SYSTEM -> if (isDarkTheme) MaterialTheme.colorScheme.onSurface else Color.White
        ThemeMode.DARK -> MaterialTheme.colorScheme.onSurface
        ThemeMode.LIGHT -> Color.White
    }
    val haptic = LocalHapticFeedback.current
    // Swipe gesture state
    var swipeOffset by remember { mutableStateOf(0f) }
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipeOffset"
    )
    // Threshold to trigger action
    val swipeThreshold = 120f
    var swipeTriggered by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Box(
            modifier = Modifier
                .pointerInput(onCopy, onSpeak) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (!swipeTriggered && kotlin.math.abs(swipeOffset) > swipeThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (swipeOffset > 0) onCopy() else onSpeak()
                            }
                            swipeTriggered = false
                            swipeOffset = 0f
                        },
                        onDragCancel = { swipeOffset = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            swipeOffset = (swipeOffset + dragAmount).coerceIn(-200f, 200f)
                        }
                    )
                }
                .graphicsLayer { translationX = animatedSwipeOffset }
        ) {
            // Swipe hint backgrounds
            if (kotlin.math.abs(animatedSwipeOffset) > 20f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            if (animatedSwipeOffset > 0)
                                NexaAccent.copy(alpha = (kotlin.math.abs(animatedSwipeOffset) / 400f).coerceAtMost(0.15f))
                            else
                                Color(0xFF6C63FF).copy(alpha = (kotlin.math.abs(animatedSwipeOffset) / 400f).coerceAtMost(0.15f))
                        )
                )
            }

        Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (isUser) 20.dp else 6.dp, bottomEnd = if (isUser) 6.dp else 20.dp),
            color = if (isUser) userBubbleColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
                        Text(message.attachmentName, fontSize = 12.sp, color = if (isUser) userTextColor else NexaAccent, fontWeight = FontWeight.Medium)
                    }
                    if (message.content.length > message.attachmentName.length + 3) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(message.content.removePrefix("📎 ${message.attachmentName}\n"), fontSize = 15.sp, lineHeight = 22.sp, color = if (isUser) userTextColor else MaterialTheme.colorScheme.onSurface)
                    }
                } else if (message.isStreaming && message.content.isEmpty()) {
                    DotsTyping()
                } else {
                    val markdownText = rememberMarkdownText(message.content)
                    Text(text = markdownText, fontSize = 15.sp, lineHeight = 22.sp,
                        color = if (isUser) userTextColor else MaterialTheme.colorScheme.onSurface)
                }
                // Action buttons INSIDE the message bubble, after the text
                if (!isUser && !message.isStreaming && message.content.isNotEmpty()) {
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        if (onRegenerate != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                            DropdownMenuItem(
                                text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp), tint = NexaAccent); Text(NexaStrings.get("regenerate", language)) } },
                                onClick = { showMsgMenu = false; onRegenerate() }
                            )
                        }
                    }
                }
                // Clear chat button (only on last assistant message)
                if (isLastAssistant) {
                    Surface(onClick = onClearChat, shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent, modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
                // Stop speaking button (only when globally speaking)
                if (isGloballySpeaking) {
                    Surface(onClick = onStopSpeaking, shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                        }
                    }
                }
                }
            }
        }
        } // swipe gesture Box
    }
}

@Composable
fun ThinkingIndicator(lang: AppLanguage) {
    // Neon sinusoidal wave indicator
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing)
        ),
        label = "wavePhase"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sinusoidal neon wave
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(20.dp)
                .drawBehind {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f
                    val amplitude = height * 0.35f
                    val segments = 40

                    // Glow layer
                    for (i in 0 until segments - 1) {
                        val x1 = width * i / segments
                        val x2 = width * (i + 1) / segments
                        val y1 = centerY + amplitude * kotlin.math.sin(phase + i * 0.5f)
                        val y2 = centerY + amplitude * kotlin.math.sin(phase + (i + 1) * 0.5f)
                        drawLine(
                            color = NexaAccent.copy(alpha = glowAlpha * 0.15f),
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 8.dp.toPx()
                        )
                    }
                    // Main neon line
                    for (i in 0 until segments - 1) {
                        val x1 = width * i / segments
                        val x2 = width * (i + 1) / segments
                        val y1 = centerY + amplitude * kotlin.math.sin(phase + i * 0.5f)
                        val y2 = centerY + amplitude * kotlin.math.sin(phase + (i + 1) * 0.5f)
                        drawLine(
                            color = NexaAccent.copy(alpha = glowAlpha),
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
        )
        Text(
            NexaStrings.get("thinking", lang),
            fontSize = 12.sp,
            color = NexaAccent.copy(alpha = glowAlpha * 0.5f),
            letterSpacing = 0.5.sp,
            fontWeight = FontWeight.Medium
        )
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
//  SHIMMER LOADING EFFECT
// ═══════════════════════════════════════

@Composable
fun ShimmerLoading(isDarkTheme: Boolean = true) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val baseColor = if (isDarkTheme) Color(0xFF1A1A26) else Color(0xFFF0F1F5)
    val highlightColor = if (isDarkTheme) Color(0xFF2A2A3A) else Color(0xFFE0E2EA)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Shimmer bubble - simulates AI response
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            color = baseColor,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                // NEXA label shimmer
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(baseColor)
                    )
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(baseColor)
                    )
                }
                // Content line shimmers
                repeat(3) { lineIndex ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                when (lineIndex) {
                                    0 -> 0.9f
                                    1 -> 0.7f
                                    else -> 0.45f
                                }
                            )
                            .height(12.dp)
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .drawBehind {
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            baseColor,
                                            highlightColor,
                                            baseColor
                                        ),
                                        startX = shimmerTranslate - 100f,
                                        endX = shimmerTranslate + 200f
                                    ),
                                    cornerRadius = CornerRadius(6.dp.toPx())
                                )
                            }
                    )
                }
            }
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



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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.nexa.ai.R
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
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
    isSpeaking: Boolean = false, onActivateVoiceMode: () -> Unit = {},
    onShareMessage: (String) -> Unit = {},
    onQuickAction: (String) -> Unit = {}) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LazyColumn(modifier = modifier.fillMaxWidth(), state = listState,
        contentPadding = chatContentPadding(),
        verticalArrangement = Arrangement.spacedBy(NexaSpacing.itemSpacing())) {
        if (messages.isEmpty()) item { EmptyState(language, onActivateVoiceMode, onQuickAction) }
        items(messages, key = { it.id }) { msg ->
            val isLast = msg == messages.lastOrNull()
            val isLastAssistant = isLast && msg.role == "assistant" && !msg.isStreaming && msg.content.isNotEmpty()
            MessageBubble(message = msg, isSpeaking = speakingMessageId == msg.id, language = language,
                isDarkTheme = isDarkTheme, themeMode = themeMode,
                onSpeak = { onSpeakMessage(msg.content, msg.id) }, onCopy = { onCopyMessage(msg.content) },
                onExport = { onExportMessage(msg) }, onRegenerate = if (isLastAssistant) onRegenerate else null,
                isLastAssistant = isLastAssistant, onClearChat = onClearChat,
                onStopSpeaking = onStopSpeaking, isGloballySpeaking = isSpeaking,
                onShare = { onShareMessage(msg.content) })
        }
        if (isThinking && messages.isEmpty()) item { ShimmerLoading(isDarkTheme = isDarkTheme) }
        if (isThinking && messages.isNotEmpty()) item { ThinkingIndicator(language) }
    }
}

@Composable
fun EmptyState(lang: AppLanguage, onActivateVoiceMode: () -> Unit = {}, onQuickAction: (String) -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = NexaSizes.emptyStateTopPadding(), bottom = 40.dp),
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
            Text("NEXA", fontSize = adaptiveText(14.sp), fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface, letterSpacing = 6.sp)
            Box(modifier = Modifier.width(30.dp).height(1.dp).background(NexaAccent.copy(alpha = 0.5f)))
        }

        // Welcome message
        Text(
            NexaStrings.get("welcome_msg", lang),
            fontSize = adaptiveText(14.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            letterSpacing = 0.3.sp,
            lineHeight = 22.sp
        )

        // Voice activation hint (below welcome text)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .clickable { onActivateVoiceMode() }
                .padding(horizontal = 24.dp, vertical = 12.dp)
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
                    tint = NexaAccent.copy(alpha = 0.9f)
                )
            }
            Text(
                NexaStrings.get("activate_voice", lang),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                letterSpacing = 0.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ── Quick Actions ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                NexaStrings.get("quick_actions", lang),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                letterSpacing = 2.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                QuickActionChip(
                    emoji = "🎨",
                    label = NexaStrings.get("create_image", lang),
                    onClick = { onQuickAction("image") }
                )
                QuickActionChip(
                    emoji = "🌐",
                    label = NexaStrings.get("create_web", lang),
                    onClick = { onQuickAction("web") }
                )
                QuickActionChip(
                    emoji = "⭐",
                    label = NexaStrings.get("create_logo", lang),
                    onClick = { onQuickAction("logo") }
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                QuickActionChip(
                    emoji = "💻",
                    label = NexaStrings.get("write_code", lang),
                    onClick = { onQuickAction("code") }
                )
                QuickActionChip(
                    emoji = "📷",
                    label = NexaStrings.get("vision_camera", lang),
                    onClick = { onQuickAction("vision") }
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(emoji: String, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(0.5.dp, NexaAccent.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(emoji, fontSize = 14.sp)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                letterSpacing = 0.3.sp)
        }
    }
}

// ═══════════════════════════════════════
//  MESSAGE IMAGE RENDERING
// ═══════════════════════════════════════

private sealed class MessageSegment {
    data class Text(val content: String) : MessageSegment()
    data class Image(val url: String, val alt: String) : MessageSegment()
}

@Composable
private fun MessageImage(url: String, alt: String) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 300.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(500)
                        .build(),
                    contentDescription = alt.ifEmpty { stringResource(R.string.generated_image) },
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Generando imagen...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    },
                    error = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "Error al cargar imagen",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Toca para reintentar",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                )
            }
            if (alt.isNotEmpty()) {
                Text(
                    alt,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, isSpeaking: Boolean, language: AppLanguage,
    isDarkTheme: Boolean = true, themeMode: ThemeMode = ThemeMode.DARK,
    onSpeak: () -> Unit, onCopy: () -> Unit, onExport: () -> Unit, onRegenerate: (() -> Unit)? = null,
    isLastAssistant: Boolean = false, onClearChat: () -> Unit = {},
    onStopSpeaking: () -> Unit = {}, isGloballySpeaking: Boolean = false,
    onShare: () -> Unit = {}) {
    val isUser = message.role == "user"

    // Dynamic color for user bubble: SYSTEM mode uses Material You, others use custom colors
    val dynamicPrimary = dynamicPrimaryColor()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val userBubbleColor = remember(themeMode, isDarkTheme, dynamicPrimary) {
        when (themeMode) {
            ThemeMode.SYSTEM -> {
                if (isDarkTheme) dynamicPrimary.copy(alpha = 0.15f)
                else dynamicPrimary.copy(alpha = 0.85f)
            }
            ThemeMode.DARK -> NexaUserBubbleDark
            ThemeMode.LIGHT -> NexaUserBubbleLight
        }
    }
    val userTextColor = remember(themeMode, isDarkTheme, onSurface) {
        when (themeMode) {
            ThemeMode.SYSTEM -> if (isDarkTheme) onSurface else Color.White
            ThemeMode.DARK -> onSurface
            ThemeMode.LIGHT -> Color.White
        }
    }
    val haptic = LocalHapticFeedback.current

    // Premium cubic-bezier entry animation
    val entryProgress = remember { Animatable(0f) }
    LaunchedEffect(message.id) {
        entryProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 500,
                easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
            )
        )
    }

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entryProgress.value
                scaleX = 0.92f + 0.08f * entryProgress.value
                scaleY = 0.92f + 0.08f * entryProgress.value
                translationY = 40f * (1f - entryProgress.value)
            },
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        val bubbleMaxWidth = NexaSizes.messageBubbleMaxWidth()
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = bubbleMaxWidth)
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
                    // Optimized: Split content into text and image segments once
                    val segments = remember(message.content) {
                        val pattern = Regex("!\\[([^]]*)]\\((https?://[^)]+)\\)")
                        val result = mutableListOf<MessageSegment>()
                        var lastIdx = 0

                        pattern.findAll(message.content).forEach { match ->
                            if (match.range.first > lastIdx) {
                                val textBefore = message.content.substring(lastIdx, match.range.first)
                                if (textBefore.isNotBlank()) result.add(MessageSegment.Text(textBefore))
                            }
                            result.add(MessageSegment.Image(match.groupValues[2], match.groupValues[1]))
                            lastIdx = match.range.last + 1
                        }

                        if (lastIdx < message.content.length) {
                            val remainingText = message.content.substring(lastIdx)
                            if (remainingText.isNotBlank()) result.add(MessageSegment.Text(remainingText))
                        }
                        result
                    }

                    // Render segments
                    if (segments.isEmpty()) {
                        Text(message.content, fontSize = 15.sp, lineHeight = 22.sp, color = if (isUser) userTextColor else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            segments.forEach { segment ->
                                when (segment) {
                                    is MessageSegment.Text -> Text(segment.content, fontSize = 15.sp, lineHeight = 22.sp, color = if (isUser) userTextColor else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start)
                                    is MessageSegment.Image -> MessageImage(segment.url, segment.alt)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        if (isLastAssistant || (!isUser && !message.isStreaming && message.content.isNotEmpty())) {
            Row(modifier = Modifier.padding(start = 4.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimatedVisibility(isSpeaking, enter = fadeIn(), exit = fadeOut()) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)), tint = NexaAccent.copy(alpha = 0.6f))
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                if (!isSpeaking) {
                    IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                } else {
                    IconButton(onClick = onStopSpeaking, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp), tint = NexaAccent.copy(alpha = 0.6f))
                    }
                }
                IconButton(onClick = onExport, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
                if (isLastAssistant) {
                    IconButton(onClick = { onRegenerate?.invoke() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator(language: AppLanguage) {
    Row(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(NexaAccent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text("⚡", fontSize = 5.sp)
        }
        Text(
            NexaStrings.get("thinking", language),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(start = 4.dp)) {
            repeat(3) {
                val infiniteTransition = rememberInfiniteTransition(label = "dot$it")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dotAlpha$it"
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
private fun DotsTyping() {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(4.dp)) {
        repeat(3) {
            val infiniteTransition = rememberInfiniteTransition(label = "typing$it")
            val translationY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotY$it"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    .graphicsLayer { this.translationY = translationY }
            )
        }
    }
}

@Composable
fun ShimmerLoading(isDarkTheme: Boolean = true) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
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
    val hPad = AdaptivePadding.horizontal()
    val vPad = AdaptivePadding.vertical()
    val btnSize = AdaptivePadding.button()

    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
        shadowElevation = 0.dp, border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))) {
        Column(modifier = Modifier.padding(horizontal = hPad, vertical = vPad)) {
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Attach menu
                Box {
                    Surface(onClick = { showMenu = true }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)), modifier = Modifier.size(btnSize)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(btnSize * 0.47f)) }
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Photo, null, modifier = Modifier.size(20.dp), tint = NexaAccent); Text(NexaStrings.get("upload_photo", language), fontSize = 14.sp) } }, onClick = { showMenu = false; onAttachFile() })
                        DropdownMenuItem(text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(20.dp), tint = NexaAccent); Text(NexaStrings.get("upload_pdf", language), fontSize = 14.sp) } }, onClick = { showMenu = false; onAttachFile() })
                    }
                }

                // Text input
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(value = text, onValueChange = onTextChange,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = btnSize),
                            placeholder = { Text(if (isListening) NexaStrings.get("listening", language) else NexaStrings.get("input_hint", language), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 14.sp, letterSpacing = 0.3.sp) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend(); keyboardController?.hide() }),
                            maxLines = 4, textStyle = LocalTextStyle.current.copy(fontSize = 14.sp))
                        Surface(onClick = { if (isListening) onStopListening() else onStartListening() }, shape = CircleShape,
                            color = if (isListening) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else Color.Transparent, modifier = Modifier.size(btnSize * 0.89f)) {
                            Box(contentAlignment = Alignment.Center) { Icon(if (isListening) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, tint = if (isListening) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else NexaAccent.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) }
                        }
                    }
                }

                // Send button
                val canSend = text.isNotBlank() || pendingAttachment != null
                Surface(onClick = { onSend(); keyboardController?.hide() }, enabled = canSend, shape = CircleShape,
                    color = if (canSend) NexaAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = if (canSend) BorderStroke(1.dp, NexaAccent.copy(alpha = 0.3f)) else null, modifier = Modifier.size(btnSize)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = NexaStrings.get("send", language), tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(btnSize * 0.44f)) }
                }
            }

            // Hint
            Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.Center) {
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

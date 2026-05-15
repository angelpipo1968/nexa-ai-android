package com.nexa.ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.viewmodel.*
import kotlinx.coroutines.launch

// ═══════════════════════════════════════
//  CHAT MAIN SCREEN
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMainScreen(
    uiState: NexaUiState,
    isDarkTheme: Boolean,
    onSend: () -> Unit,
    onInputChange: (String) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSpeakMessage: (String, String) -> Unit,
    onToggleVoiceMode: () -> Unit = {},
    onStopVoiceMode: () -> Unit = {},
    onClearChat: () -> Unit,
    onDismissError: () -> Unit,
    onToggleDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onCreateSession: () -> Unit,
    onSwitchSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onToggleSettings: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetVoiceType: (VoiceType) -> Unit,
    onCycleTheme: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    onNavigateToLogin: () -> Unit,
    onLogout: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onExportMessage: (Message) -> Unit,
    onSurpriseMe: () -> Unit,
    onSetDrawerView: (Int) -> Unit,
    onAttachFile: () -> Unit,
    onClearAttachment: () -> Unit = {},
    onNavigateToLottery: () -> Unit = {},
    onPinSession: (String) -> Unit = {},
    onRenameSession: (String) -> Unit = {},
    onCloneSession: (String) -> Unit = {},
    onArchiveSession: (String) -> Unit = {},
    onShareSession: (String) -> Unit = {},
    onDownloadSession: (String) -> Unit = {},
    onRegenerate: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Sync drawer: ViewModel → UI only (avoid double-sync race condition)
    LaunchedEffect(uiState.drawerOpen) {
        if (uiState.drawerOpen && drawerState.isClosed) drawerState.open()
        else if (!uiState.drawerOpen && drawerState.isOpen) drawerState.close()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                uiState = uiState, onNewChat = onCreateSession,
                onSwitchSession = onSwitchSession, onDeleteSession = onDeleteSession,
                onClose = { coroutineScope.launch { drawerState.close() } },
                onNavigateToLogin = onNavigateToLogin, onLogout = onLogout,
                onSetLanguage = onSetLanguage, onSetVoiceType = onSetVoiceType,
                onToggleTheme = onCycleTheme, onToggleSettings = onToggleSettings,
                onToggleAutoSpeak = onToggleAutoSpeak, onSetDrawerView = onSetDrawerView,
                onNavigateToLottery = onNavigateToLottery,
                onPinSession = onPinSession, onRenameSession = onRenameSession,
                onCloneSession = onCloneSession, onArchiveSession = onArchiveSession,
                onShareSession = onShareSession, onDownloadSession = onDownloadSession
            )
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(uiState = uiState, onToggleDrawer = onToggleDrawer,
                    onToggleAutoSpeak = onToggleAutoSpeak, onStopSpeaking = onStopSpeaking,
                    onClearChat = onClearChat, onSurpriseMe = onSurpriseMe,
                    onToggleSettings = onToggleSettings)
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = uiState.error != null) {
                        ErrorBanner(uiState.error ?: "", onDismissError)
                    }

                    // Pull-to-refresh gesture for clearing chat
                    val haptic = LocalHapticFeedback.current
                    var pullOffset by remember { mutableStateOf(0f) }
                    val animatedPullOffset by animateFloatAsState(
                        targetValue = pullOffset,
                        animationSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "pullOffset"
                    )
                    val pullThreshold = 150f
                    var refreshTriggered by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(uiState.messages.isNotEmpty()) {
                                if (uiState.messages.isNotEmpty()) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            if (pullOffset > pullThreshold && !refreshTriggered) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onClearChat()
                                            }
                                            refreshTriggered = false
                                            pullOffset = 0f
                                        },
                                        onDragCancel = { pullOffset = 0f },
                                        onVerticalDrag = { _, dragAmount ->
                                            if (dragAmount > 0) {
                                                pullOffset = (pullOffset + dragAmount).coerceAtMost(250f)
                                            }
                                        }
                                    )
                                }
                            }
                    ) {
                        // Pull indicator
                        if (animatedPullOffset > 20f) {
                            val progress = (animatedPullOffset / pullThreshold).coerceAtMost(1f)
                            val infiniteTransition = rememberInfiniteTransition(label = "pullGlow")
                            val glowAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f, targetValue = 0.8f,
                                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                                label = "pullGlow"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(animatedPullOffset.dp * 0.4f)
                                    .graphicsLayer { alpha = progress * 0.8f },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size((16 + 8 * progress).dp),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = glowAlpha * progress)
                                    )
                                    if (progress > 0.7f) {
                                        Text(
                                            if (uiState.language == AppLanguage.SPANISH) "Soltar para limpiar" else "Release to clear",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.error.copy(alpha = glowAlpha * 0.6f),
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }

                        ChatMessages(
                            messages = uiState.messages,
                            isThinking = uiState.isThinking,
                            language = uiState.language,
                            speakingMessageId = uiState.speakingMessageId,
                            onSpeakMessage = onSpeakMessage,
                            onCopyMessage = onCopyMessage,
                            onExportMessage = onExportMessage,
                            onRegenerate = onRegenerate,
                            isDarkTheme = isDarkTheme,
                            themeMode = uiState.themeMode,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationY = animatedPullOffset * 0.3f },
                            onClearChat = onClearChat,
                            onStopSpeaking = onStopSpeaking,
                            isSpeaking = uiState.isSpeaking,
                            onActivateVoiceMode = onToggleVoiceMode
                        )
                    }

                    InputBar(text = uiState.inputText, language = uiState.language,
                        isListening = uiState.isListening, isSpeaking = uiState.isSpeaking,
                        pendingAttachment = uiState.pendingAttachment, onTextChange = onInputChange,
                        onSend = onSend, onStartListening = onStartListening,
                        onStopListening = onStopListening, onStopSpeaking = onStopSpeaking,
                        onAttachFile = onAttachFile, onClearAttachment = onClearAttachment)
                }

                // Voice Mode Overlay
                if (uiState.voiceMode) {
                    VoiceModeOverlay(
                        uiState = uiState,
                        onStopVoiceMode = onStopVoiceMode
                    )
                }

                // Voice Mode FAB (when not in voice mode)
                if (!uiState.voiceMode) {
                    VoiceModeFab(
                        onToggleVoiceMode = onToggleVoiceMode,
                        language = uiState.language
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
//  VOICE MODE OVERLAY — FUTURIST
// ═══════════════════════════════════════

@Composable
fun VoiceModeOverlay(
    uiState: NexaUiState,
    onStopVoiceMode: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voiceMode")

    // ── Orb animations ──
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOut), RepeatMode.Reverse),
        label = "r1"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(2800, easing = EaseInOut), RepeatMode.Reverse),
        label = "r2"
    )
    val ring3Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(3400, easing = EaseInOut), RepeatMode.Reverse),
        label = "r3"
    )
    val coreGlow by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse),
        label = "coreGlow"
    )
    val coreScale by infiniteTransition.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOut), RepeatMode.Reverse),
        label = "coreScale"
    )

    // ── Wave animation ──
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "wavePhase"
    )
    val waveAmplitude by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOut), RepeatMode.Reverse),
        label = "waveAmp"
    )

    // ── Rotation for outer ring ──
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "rotation"
    )

    // ── State logic ──
    val isListening = uiState.isListening
    val isThinking = uiState.isThinking
    val isSpeaking = uiState.isSpeaking

    val accentColor = when {
        isListening -> NexaAccent           // Green
        isThinking -> Color(0xFF7C6AFF)     // Violet
        isSpeaking -> Color(0xFF00E5D0)     // Cyan
        else -> NexaAccent
    }
    val accentDim = accentColor.copy(alpha = 0.15f)
    val accentMid = accentColor.copy(alpha = 0.35f)

    val stateLabel = when {
        isListening -> NexaStrings.get("voice_mode_listening", uiState.language)
        isThinking -> NexaStrings.get("voice_mode_thinking", uiState.language)
        isSpeaking -> NexaStrings.get("voice_mode_speaking", uiState.language)
        else -> NexaStrings.get("voice_mode_hint", uiState.language)
    }
    val stateSubtext = when {
        isListening -> NexaStrings.get("voice_mode_hint", uiState.language)
        isThinking -> "NEXA PRO"
        isSpeaking -> "NEXA PRO"
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)),
        contentAlignment = Alignment.Center
    ) {
        // ── Background subtle grid ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gridColor = Color.White.copy(alpha = 0.015f)
            val step = 40f
            var x = 0f
            while (x < w) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 0.5f)
                x += step
            }
            var y = 0f
            while (y < h) {
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.5f)
                y += step
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // ═══ THE ORB ═══
            Box(contentAlignment = Alignment.Center) {

                // Outermost ring — slow rotation, dashed
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .graphicsLayer {
                            rotationZ = rotation
                            alpha = 0.12f
                        }
                        .drawBehind {
                            val stroke = 1.5.dp.toPx()
                            val dashLen = 12.dp.toPx()
                            val gapLen = 8.dp.toPx()
                            val r = size.minDimension / 2f
                            val paint = android.graphics.Paint().apply {
                                color = accentColor.copy(alpha = 0.12f).toArgb()
                                strokeWidth = stroke
                                style = android.graphics.Paint.Style.STROKE
                                pathEffect = android.graphics.DashPathEffect(floatArrayOf(dashLen, gapLen), 0f)
                                isAntiAlias = true
                            }
                            drawContext.canvas.nativeCanvas.drawCircle(
                                size.width / 2f, size.height / 2f, r, paint
                            )
                        }
                )

                // Ring 3 — outermost glow ring
                Box(
                    modifier = Modifier
                        .size((170 * ring3Scale).dp)
                        .graphicsLayer { alpha = 0.08f }
                        .clip(CircleShape)
                        .background(accentDim)
                )

                // Ring 2 — middle ring
                Box(
                    modifier = Modifier
                        .size((140 * ring2Scale).dp)
                        .graphicsLayer { alpha = 0.12f }
                        .clip(CircleShape)
                        .background(accentMid)
                )

                // Ring 1 — inner ring
                Box(
                    modifier = Modifier
                        .size((110 * ring1Scale).dp)
                        .graphicsLayer { alpha = 0.18f }
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.2f))
                )

                // Sound wave visualization ring
                Canvas(
                    modifier = Modifier
                        .size(160.dp)
                        .graphicsLayer { alpha = if (isListening || isSpeaking) 0.6f else 0.15f }
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val baseRadius = size.minDimension / 2f - 10.dp.toPx()
                    val segments = 60
                    val amp = 12.dp.toPx() * waveAmplitude * if (isListening) 1f else if (isSpeaking) 0.7f else 0.2f

                    for (i in 0 until segments) {
                        val angle1 = (2f * Math.PI.toFloat() * i / segments)
                        val angle2 = (2f * Math.PI.toFloat() * (i + 1) / segments)
                        val wave1 = baseRadius + amp * kotlin.math.sin(wavePhase * 3 + i * 0.4f)
                        val wave2 = baseRadius + amp * kotlin.math.sin(wavePhase * 3 + (i + 1) * 0.4f)
                        val x1 = cx + wave1 * kotlin.math.cos(angle1)
                        val y1 = cy + wave1 * kotlin.math.sin(angle1)
                        val x2 = cx + wave2 * kotlin.math.cos(angle2)
                        val y2 = cy + wave2 * kotlin.math.sin(angle2)
                        drawLine(
                            color = accentColor.copy(alpha = 0.4f + 0.3f * kotlin.math.sin(wavePhase + i * 0.2f)),
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Core orb — the main circle
                Box(
                    modifier = Modifier
                        .size((80 * coreScale).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    accentColor.copy(alpha = coreGlow),
                                    accentColor.copy(alpha = coreGlow * 0.4f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner icon
                    Icon(
                        imageVector = when {
                            isListening -> Icons.Default.Mic
                            isThinking -> Icons.Default.AutoAwesome
                            isSpeaking -> Icons.Default.VolumeUp
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ═══ STATUS TEXT ═══
            AnimatedContent(
                targetState = stateLabel,
                transitionSpec = {
                    fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 } togetherWith
                    fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 4 }
                },
                label = "stateLabel"
            ) { label ->
                Text(
                    label,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light,
                    color = accentColor.copy(alpha = 0.9f),
                    letterSpacing = 2.sp
                )
            }

            if (stateSubtext.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stateSubtext,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.15f),
                    letterSpacing = 4.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ═══ STOP BUTTON ═══
            Surface(
                onClick = onStopVoiceMode,
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) NexaAccent
                                else if (isSpeaking) Color(0xFF00E5D0)
                                else Color(0xFF7C6AFF)
                            )
                    )
                    Text(
                        NexaStrings.get("tap_to_stop", uiState.language),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.35f),
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
//  VOICE MODE FAB — FUTURIST
// ═══════════════════════════════════════

@Composable
fun VoiceModeFab(
    onToggleVoiceMode: () -> Unit,
    language: AppLanguage
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "fabPulse")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.08f, targetValue = 0.22f,
            animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOut), RepeatMode.Reverse),
            label = "fabGlow"
        )
        val ringScale by infiniteTransition.animateFloat(
            initialValue = 0.9f, targetValue = 1.15f,
            animationSpec = infiniteRepeatable(tween(2800, easing = EaseInOut), RepeatMode.Reverse),
            label = "fabRing"
        )

        Box(contentAlignment = Alignment.Center) {
            // Outer pulse ring
            Box(
                modifier = Modifier
                    .size((64 * ringScale).dp)
                    .clip(CircleShape)
                    .background(NexaAccent.copy(alpha = glowAlpha * 0.4f))
            )
            // Main button
            Surface(
                onClick = onToggleVoiceMode,
                shape = CircleShape,
                color = Color(0xFF0D1117),
                border = BorderStroke(1.dp, NexaAccent.copy(alpha = 0.3f + glowAlpha)),
                modifier = Modifier
                    .padding(end = 20.dp, bottom = 80.dp)
                    .size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = NexaStrings.get("voice_mode", language),
                        modifier = Modifier.size(22.dp),
                        tint = NexaAccent.copy(alpha = 0.8f + glowAlpha * 0.5f)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
//  DRAWER
// ═══════════════════════════════════════

@Composable
fun DrawerContent(
    uiState: NexaUiState, onNewChat: () -> Unit, onSwitchSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit, onClose: () -> Unit, onNavigateToLogin: () -> Unit,
    onLogout: () -> Unit, onSetLanguage: (AppLanguage) -> Unit, onSetVoiceType: (VoiceType) -> Unit,
    onToggleTheme: () -> Unit, onToggleSettings: () -> Unit, onToggleAutoSpeak: () -> Unit,
    onSetDrawerView: (Int) -> Unit, onNavigateToLottery: () -> Unit = {},
    onPinSession: (String) -> Unit = {}, onRenameSession: (String) -> Unit = {},
    onCloneSession: (String) -> Unit = {}, onArchiveSession: (String) -> Unit = {},
    onShareSession: (String) -> Unit = {}, onDownloadSession: (String) -> Unit = {}
) {
    val sessions = uiState.sessions
    val activeSessionId = uiState.activeSessionId
    val user = uiState.user
    val lang = uiState.language
    var searchQuery by remember { mutableStateOf("") }
    val filteredSessions = if (searchQuery.isBlank()) sessions else
        sessions.filter { it.title.contains(searchQuery, ignoreCase = true) || it.messages.any { m -> m.content.contains(searchQuery, ignoreCase = true) } }

    ModalDrawerSheet(modifier = Modifier.width(300.dp), drawerContainerColor = MaterialTheme.colorScheme.surface) {
        // Header with parallax effect
        val drawerListState = rememberLazyListState()
        val headerParallaxOffset by remember {
            derivedStateOf { (drawerListState.firstVisibleItemScrollOffset * 0.4f) }
        }
        val headerAlpha by remember {
            derivedStateOf { (1f - (drawerListState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 0.6f)) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = -headerParallaxOffset
                    alpha = headerAlpha
                }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            NexaAccent.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Animated glow on drawer logo
                val infiniteTransition = rememberInfiniteTransition(label = "drawerGlow")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.10f, targetValue = 0.22f,
                    animationSpec = infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "drawerGlowAlpha"
                )
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.radialGradient(listOf(NexaAccent.copy(alpha = glowAlpha), NexaAccent.copy(alpha = 0.03f)))),
                    contentAlignment = Alignment.Center) { Text("⚡", fontSize = 20.sp) }
                Column(modifier = Modifier.weight(1f)) {
                    Text("NEXA PRO", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 3.sp)
                    if (user.isLoggedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(NexaAccent))
                            Text(user.displayName, fontSize = 10.sp, color = NexaAccent.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
                        }
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                }
            }
        }

        // New chat button
        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { onNewChat() },
            shape = RoundedCornerShape(14.dp), color = NexaAccent.copy(alpha = 0.06f),
            border = BorderStroke(0.5.dp, NexaAccent.copy(alpha = 0.12f))) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = NexaAccent.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                Text(NexaStrings.get("new_chat", lang), color = NexaAccent.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.5.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Search bar
        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(NexaStrings.get("search_chats", lang), fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(18.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Session list
        if (filteredSessions.isEmpty()) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                Text(NexaStrings.get(if (searchQuery.isEmpty()) "no_chats" else "no_results", lang),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), state = drawerListState, contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(filteredSessions, key = { it.id }) { session ->
                    ChatSessionItem(session = session, language = lang, isActive = session.id == activeSessionId,
                        onClick = { onSwitchSession(session.id) }, onDelete = { onDeleteSession(session.id) },
                        onPin = { onPinSession(session.id) }, onRename = { onRenameSession(session.id) },
                        onClone = { onCloneSession(session.id) }, onArchive = { onArchiveSession(session.id) },
                        onShare = { onShareSession(session.id) }, onDownload = { onDownloadSession(session.id) })
                }
            }
        }

        // Lottery button (hidden - uncomment to restore)
        // Surface(
        //     modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        //         .clickable { onNavigateToLottery(); onClose() },
        //     shape = RoundedCornerShape(12.dp),
        //     color = NexaAccent.copy(alpha = 0.04f),
        //     border = BorderStroke(0.5.dp, NexaAccent.copy(alpha = 0.08f))
        // ) {
        //     Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        //         verticalAlignment = Alignment.CenterVertically,
        //         horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        //         Text("🎰", fontSize = 16.sp)
        //         Text("Lotería", fontSize = 13.sp, fontWeight = FontWeight.Medium,
        //             color = NexaAccent.copy(alpha = 0.7f))
        //     }
        // }

        // Bottom actions
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onToggleSettings(); onClose() }
                .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Settings, contentDescription = NexaStrings.get("settings", lang),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                Text(NexaStrings.get("settings", lang), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 0.3.sp)
            }
            Row(modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                if (user.isLoggedIn) onLogout() else onNavigateToLogin()
            }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (user.isLoggedIn) Icons.AutoMirrored.Filled.ExitToApp else Icons.Default.Person, contentDescription = null,
                    tint = if (user.isLoggedIn) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else NexaAccent.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp))
                Text(if (user.isLoggedIn) NexaStrings.get("logout", lang) else NexaStrings.get("login", lang),
                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = if (user.isLoggedIn) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    letterSpacing = 0.3.sp)
            }
        }
    }
}

@Composable
fun ChatSessionItem(
    session: ChatSession, language: AppLanguage, isActive: Boolean,
    onClick: () -> Unit, onDelete: () -> Unit,
    onPin: () -> Unit = {}, onRename: () -> Unit = {},
    onClone: () -> Unit = {}, onArchive: () -> Unit = {},
    onShare: () -> Unit = {}, onDownload: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp), color = if (isActive) NexaAccent.copy(alpha = 0.06f) else Color.Transparent,
        border = if (isActive) BorderStroke(0.5.dp, NexaAccent.copy(alpha = 0.1f)) else null) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp),
                tint = if (isActive) NexaAccent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(session.title.ifEmpty { NexaStrings.get("new_chat", language) }, fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = 0.2.sp)
                Text("${session.messages.size} ${NexaStrings.get("messages_count", language)}", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.PushPin, null, modifier = Modifier.size(16.dp), tint = NexaAccent.copy(alpha = 0.7f))
                            Text(NexaStrings.get("pin_chat", language), fontSize = 13.sp)
                        }},
                        onClick = { showMenu = false; onPin() })
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text(NexaStrings.get("rename_chat", language), fontSize = 13.sp)
                        }},
                        onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text(NexaStrings.get("clone_chat", language), fontSize = 13.sp)
                        }},
                        onClick = { showMenu = false; onClone() })
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Archive, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text(NexaStrings.get("archive_chat", language), fontSize = 13.sp)
                        }},
                        onClick = { showMenu = false; onArchive() })
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text(NexaStrings.get("share_chat", language), fontSize = 13.sp)
                        }},
                        onClick = { showMenu = false; onShare() })
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text(NexaStrings.get("download_chat", language), fontSize = 13.sp)
                        }},
                        onClick = { showMenu = false; onDownload() })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                            Text(NexaStrings.get("delete_chat", language), fontSize = 13.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                        }},
                        onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}

// ═══════════════════════════════════════
//  TOP BAR
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(uiState: NexaUiState, onToggleDrawer: () -> Unit, onToggleAutoSpeak: () -> Unit,
    onStopSpeaking: () -> Unit, onClearChat: () -> Unit, onSurpriseMe: () -> Unit, onToggleSettings: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val infiniteTransition = rememberInfiniteTransition(label = "logo")
                val glowAlpha by infiniteTransition.animateFloat(initialValue = 0.12f, targetValue = 0.28f,
                    animationSpec = infiniteRepeatable(animation = tween(3000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "glow")
                Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.radialGradient(listOf(NexaAccent.copy(alpha = glowAlpha), NexaAccent.copy(alpha = 0.04f)))),
                    contentAlignment = Alignment.Center) { Text("⚡", fontSize = 16.sp) }
                Column {
                    Text("NEXA PRO", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 3.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(NexaAccent))
                        Text(NexaStrings.get("online", uiState.language), fontSize = 7.sp,
                            color = NexaAccent.copy(alpha = 0.7f), letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onToggleDrawer) {
                Icon(Icons.Default.Menu, contentDescription = NexaStrings.get("menu", uiState.language),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
            }
        },
        actions = {
            // Action buttons moved below assistant messages
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

// ═══════════════════════════════════════
//  ERROR BANNER
// ═══════════════════════════════════════

@Composable
fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error.copy(alpha = 0.06f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.1f))) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            Text(error, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), fontSize = 12.sp,
                modifier = Modifier.weight(1f), letterSpacing = 0.2.sp)
            IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Default.Close, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

package com.nexa.ai.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.*

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.graphics.nativeCanvas
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
import com.nexa.ai.ui.theme.LocalAccentColor
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
    onDismissVoiceHelp: () -> Unit = {},
    onInterruptVoice: () -> Unit = {},
    onClearChat: () -> Unit,
    onDismissError: () -> Unit,
    onToggleDrawer: () -> Unit,
    onCreateSession: () -> Unit,
    onSwitchSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onToggleSettings: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetVoiceType: (VoiceType) -> Unit,
    onCycleTheme: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onLogout: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onExportMessage: (Message) -> Unit,
    onSurpriseMe: () -> Unit,
    onSetDrawerView: (Int) -> Unit,
    onAttachFile: () -> Unit,
    onClearAttachment: () -> Unit = {},
    onNavigateToLottery: () -> Unit = {},
    onNavigateToTranslator: () -> Unit = {},
    onPinSession: (String) -> Unit = {},
    onRenameSession: (String) -> Unit = {},
    onCloneSession: (String) -> Unit = {},
    onArchiveSession: (String) -> Unit = {},
    onShareSession: (String) -> Unit = {},
    onDownloadSession: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onShareMessage: (String) -> Unit = {},
    onQuickAction: (String) -> Unit = {},
    onCaptureImage: () -> Unit = {},
    onDismissPreview: () -> Unit = {},
    onToggleHandsFree: () -> Unit = {}
) {
    val adaptiveInfo = rememberAdaptiveInfo()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.drawerOpen) {
        if (uiState.drawerOpen && drawerState.isClosed) drawerState.open()
        else if (!uiState.drawerOpen && drawerState.isOpen) drawerState.close()
    }

    if (adaptiveInfo.shouldUseDualPane) {
        // ── TABLET/LANDSCAPE: Dual-pane layout with permanent sidebar ──
        Row(modifier = Modifier.fillMaxSize()) {
            // Permanent sidebar (drawer always visible)
            Surface(
                modifier = Modifier
                    .width(adaptiveDrawerWidth())
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                DrawerContent(
                    uiState = uiState, onNewChat = onCreateSession,
                    onSwitchSession = onSwitchSession, onDeleteSession = onDeleteSession,
                    onClose = { /* No-op for permanent sidebar */ },
                    onNavigateToLogin = onNavigateToLogin, onLogout = onLogout,
                    onSetLanguage = onSetLanguage, onSetVoiceType = onSetVoiceType,
                    onToggleTheme = onCycleTheme, onToggleSettings = onToggleSettings,
                    onToggleAutoSpeak = onToggleAutoSpeak, onSetDrawerView = onSetDrawerView,
                    onNavigateToLottery = onNavigateToLottery, onNavigateToTranslator = onNavigateToTranslator,
                    onPinSession = onPinSession, onRenameSession = onRenameSession,
                    onCloneSession = onCloneSession, onArchiveSession = onArchiveSession,
                    onShareSession = onShareSession, onDownloadSession = onDownloadSession,
                    useModalSheet = false
                )
            }
            // Chat content
            ChatContentPane(
                uiState = uiState, isDarkTheme = isDarkTheme,
                onSend = onSend, onInputChange = onInputChange,
                onStartListening = onStartListening, onStopListening = onStopListening,
                onToggleAutoSpeak = onToggleAutoSpeak, onStopSpeaking = onStopSpeaking,
                onSpeakMessage = onSpeakMessage, onToggleVoiceMode = onToggleVoiceMode,
                onStopVoiceMode = onStopVoiceMode, onDismissVoiceHelp = onDismissVoiceHelp,
                onInterruptVoice = onInterruptVoice, onClearChat = onClearChat,
                onDismissError = onDismissError, onToggleDrawer = onToggleDrawer,
                onToggleSettings = onToggleSettings, onCopyMessage = onCopyMessage,
                onExportMessage = onExportMessage, onSurpriseMe = onSurpriseMe,
                onAttachFile = onAttachFile, onClearAttachment = onClearAttachment,
                onRegenerate = onRegenerate, onShareMessage = onShareMessage,
                onQuickAction = onQuickAction, onCaptureImage = onCaptureImage,
                onDismissPreview = onDismissPreview,
                onToggleHandsFree = onToggleHandsFree
            )
        }
    } else {
        // ── PHONE: Modal drawer layout (original behavior) ──
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
                    onNavigateToLottery = onNavigateToLottery, onNavigateToTranslator = onNavigateToTranslator,
                    onPinSession = onPinSession, onRenameSession = onRenameSession,
                    onCloneSession = onCloneSession, onArchiveSession = onArchiveSession,
                    onShareSession = onShareSession, onDownloadSession = onDownloadSession
                )
            },
            gesturesEnabled = true
        ) {
            ChatContentPane(
                uiState = uiState, isDarkTheme = isDarkTheme,
                onSend = onSend, onInputChange = onInputChange,
                onStartListening = onStartListening, onStopListening = onStopListening,
                onToggleAutoSpeak = onToggleAutoSpeak, onStopSpeaking = onStopSpeaking,
                onSpeakMessage = onSpeakMessage, onToggleVoiceMode = onToggleVoiceMode,
                onStopVoiceMode = onStopVoiceMode, onDismissVoiceHelp = onDismissVoiceHelp,
                onInterruptVoice = onInterruptVoice, onClearChat = onClearChat,
                onDismissError = onDismissError, onToggleDrawer = onToggleDrawer,
                onToggleSettings = onToggleSettings, onCopyMessage = onCopyMessage,
                onExportMessage = onExportMessage, onSurpriseMe = onSurpriseMe,
                onAttachFile = onAttachFile, onClearAttachment = onClearAttachment,
                onRegenerate = onRegenerate, onShareMessage = onShareMessage,
                onQuickAction = onQuickAction, onCaptureImage = onCaptureImage,
                onDismissPreview = onDismissPreview,
                onToggleHandsFree = onToggleHandsFree
            )
        }
    }
}

// ═══════════════════════════════════════
//  CHAT CONTENT PANE (extracted for dual-pane reuse)
// ═══════════════════════════════════════

@Composable
fun ChatContentPane(
    uiState: NexaUiState,
    isDarkTheme: Boolean,
    onSend: () -> Unit,
    onInputChange: (String) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSpeakMessage: (String, String) -> Unit,
    onToggleVoiceMode: () -> Unit,
    onStopVoiceMode: () -> Unit,
    onDismissVoiceHelp: () -> Unit,
    onInterruptVoice: () -> Unit,
    onClearChat: () -> Unit,
    onDismissError: () -> Unit,
    onToggleDrawer: () -> Unit,
    onToggleSettings: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onExportMessage: (Message) -> Unit,
    onSurpriseMe: () -> Unit,
    onAttachFile: () -> Unit,
    onClearAttachment: () -> Unit,
    onRegenerate: () -> Unit,
    onShareMessage: (String) -> Unit,
    onQuickAction: (String) -> Unit,
    onCaptureImage: () -> Unit,
    onDismissPreview: () -> Unit
) {
    Scaffold(
        topBar = {
            ChatTopBar(
                uiState = uiState,
                isDarkTheme = isDarkTheme,
                onToggleDrawer = onToggleDrawer,
                onClearChat = onClearChat,
                onToggleSettings = onToggleSettings
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = uiState.error != null) {
                    ErrorBanner(uiState.error ?: "", onDismissError)
                }

                if (uiState.isLoadingLocation || uiState.isSearchingFlights) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = NexaAccent,
                        trackColor = NexaAccent.copy(alpha = 0.1f)
                    )
                }

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
                                        NexaStrings.get("pull_to_clear", uiState.language),
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
                        onActivateVoiceMode = onToggleVoiceMode,
                        onShareMessage = onShareMessage,
                        onQuickAction = onQuickAction
                    )
                }

                InputBar(text = uiState.inputText, language = uiState.language,
                    isListening = uiState.isListening, isSpeaking = uiState.isSpeaking,
                    pendingAttachment = uiState.pendingAttachment, onTextChange = onInputChange,
                    onSend = onSend, onStartListening = onStartListening,
                    onStopListening = onStopListening, onStopSpeaking = onStopSpeaking,
                    onAttachFile = onAttachFile, onClearAttachment = onClearAttachment)
            }

            if (uiState.voiceMode) {
                VoiceModeOverlay(
                    uiState = uiState,
                    onStopVoiceMode = onStopVoiceMode,
                    onInterrupt = onInterruptVoice,
                    onDismissHelp = onDismissVoiceHelp
                )
            }

            // Voice commands help overlay
            if (uiState.showVoiceCommandsHelp) {
                VoiceCommandsHelpOverlay(
                    language = uiState.language,
                    onDismiss = onDismissVoiceHelp
                )
            }

            // Preview overlay for HTML/code content
            if (uiState.showPreview && uiState.previewContent != null) {
                PreviewOverlay(
                    content = uiState.previewContent,
                    language = uiState.language,
                    onDismiss = onDismissPreview
                )
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
    onStopVoiceMode: () -> Unit,
    onInterrupt: () -> Unit,
    onDismissHelp: () -> Unit
) {
    val transition = updateTransition(
        targetState = when {
            uiState.isSpeaking -> "Speaking"
            uiState.isListening -> "Listening"
            uiState.isThinking -> "Thinking"
            else -> "Idle"
        },
        label = "OrbState"
    )

    // A separate infinite transition for the pulsing effect while speaking or listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val baseScale by transition.animateFloat(
        transitionSpec = { tween(500) },
        label = "scale"
    ) { state ->
        when (state) {
            "Speaking" -> 1.3f + (uiState.voiceVolumeLevel * 0.5f)
            "Listening" -> 1.1f
            "Thinking" -> 1.0f
            else -> 0.9f
        }
    }

    val finalScale = if (uiState.isSpeaking || uiState.isListening) baseScale * pulseScale else baseScale

    val orbColor by transition.animateColor(
        transitionSpec = { tween(500) },
        label = "color"
    ) { state ->
        val accent = LocalAccentColor.current
        when (state) {
            "Speaking" -> accent
            "Listening" -> accent.copy(alpha = 0.7f)
            "Thinking" -> Color(0xFF555555)
            else -> Color(0xFF7B1FA2)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60A0A0A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (uiState.isSpeaking) onInterrupt()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(finalScale)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(orbColor.copy(alpha = 0.8f), orbColor.copy(alpha = 0.2f), Color.Transparent)
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.6f)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(orbColor)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = when {
                    uiState.isSpeaking -> "Hablando..."
                    uiState.isThinking -> "Procesando..."
                    uiState.isListening -> "Escuchando..."
                    else -> "Esperando..."
                },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }

        IconButton(
            onClick = onStopVoiceMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cerrar Modo Voz",
                tint = Color.White
            )
        }
    }
}

@Composable
fun VoiceCommandsHelpOverlay(
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    // Auto-dismiss after 8 seconds
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(8000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60A0A0F))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            // Title
            Text(
                NexaStrings.get("voice_help_title", language),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NexaAccent,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Commands list
            val commands = listOf(
                "voice_help_repeat" to "🔄",
                "voice_help_stop" to "🔇",
                "voice_help_read" to "📖",
                "voice_help_clear" to "🗑️",
                "voice_help_new" to "➕",
                "voice_help_pdf" to "📄",
                "voice_help_male" to "🗣️",
                "voice_help_female" to "🗣️",
                "voice_help_english" to "🇺🇸",
                "voice_help_spanish" to "🇪🇸",
                "voice_help_dark" to "🌙",
                "voice_help_light" to "☀️",
                "voice_help_exit" to "✋",
                "voice_help_help" to "❓"
            )

            commands.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { (key, emoji) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(emoji, fontSize = 14.sp)
                                Text(
                                    NexaStrings.get(key, language),
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    // If odd number, add spacer
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Close hint
            Text(
                NexaStrings.get("voice_help_close", language),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.25f),
                letterSpacing = 1.5.sp
            )
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
    onSetDrawerView: (Int) -> Unit, onNavigateToLottery: () -> Unit = {}, onNavigateToTranslator: () -> Unit = {},
    onPinSession: (String) -> Unit = {}, onRenameSession: (String) -> Unit = {},
    onCloneSession: (String) -> Unit = {}, onArchiveSession: (String) -> Unit = {},
    onShareSession: (String) -> Unit = {}, onDownloadSession: (String) -> Unit = {},
    useModalSheet: Boolean = true
) {
    val sessions = uiState.sessions
    val activeSessionId = uiState.activeSessionId
    val user = uiState.user
    val lang = uiState.language
    var searchQuery by remember { mutableStateOf("") }
    val filteredSessions = if (searchQuery.isBlank()) sessions else
        sessions.filter { it.title.contains(searchQuery, ignoreCase = true) || it.messages.any { m -> m.content.contains(searchQuery, ignoreCase = true) } }

    val innerContent: @Composable ColumnScope.() -> Unit = {
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
                    Text("NEXA PRO", fontWeight = FontWeight.Black, fontSize = adaptiveText(18.sp), letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (user.isLoggedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(NexaAccent))
                            Text(user.displayName, fontSize = 10.sp, color = NexaAccent.copy(alpha = 0.9f),
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

        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { onNewChat() },
            shape = RoundedCornerShape(10.dp), color = NexaAccent.copy(alpha = 0.06f),
            border = BorderStroke(0.5.dp, NexaAccent.copy(alpha = 0.12f))) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = NexaAccent.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                Text(NexaStrings.get("new_chat", lang), color = NexaAccent.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.3.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
        // Translator button
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp)).clickable { onNavigateToTranslator(); onClose() }
            .padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Language, contentDescription = "Translator",
                tint = NexaAccent.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            Text("🌍 Live Translator", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = NexaAccent.copy(alpha = 0.8f), letterSpacing = 0.3.sp)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onToggleSettings(); onClose() }
                .padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Settings, contentDescription = NexaStrings.get("settings", lang),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                Text(NexaStrings.get("settings", lang), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 0.3.sp)
            }
            Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                if (user.isLoggedIn) onLogout() else onNavigateToLogin()
            }.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
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

    if (useModalSheet) {
        ModalDrawerSheet(modifier = Modifier.width(adaptiveDrawerWidth()), drawerContainerColor = MaterialTheme.colorScheme.surface, content = innerContent)
    } else {
        Column(modifier = Modifier.fillMaxSize(), content = innerContent)
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
fun ChatTopBar(uiState: NexaUiState, isDarkTheme: Boolean, onToggleDrawer: () -> Unit, onClearChat: () -> Unit, onToggleSettings: () -> Unit = {}) {
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
                    Text("NEXA PRO", fontWeight = FontWeight.Black, fontSize = adaptiveText(15.sp), letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(NexaAccent))
                        Text(NexaStrings.get("online", uiState.language), fontSize = 8.sp,
                            color = if (isDarkTheme) NexaAccent.copy(alpha = 0.8f) else Color(0xFF007A4D), 
                            letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onToggleDrawer) {
                Icon(Icons.Default.Menu, contentDescription = NexaStrings.get("menu", uiState.language),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
            }
        },
        actions = {},
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

// ═══════════════════════════════════════
//  PREVIEW OVERLAY — View generated HTML/code
// ═══════════════════════════════════════

@Composable
fun PreviewOverlay(
    content: String,
    language: AppLanguage,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60A0A0F))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Preview,
                        contentDescription = null,
                        tint = NexaAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        NexaStrings.get("preview", language).uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexaAccent,
                        letterSpacing = 2.sp
                    )
                }
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            NexaStrings.get("close", language),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Content in scrollable surface
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF12121A),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        content,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}

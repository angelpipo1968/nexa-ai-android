package com.nexa.ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    onNavigateToLottery: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Sync drawer FROM UI → ViewModel
    LaunchedEffect(drawerState.targetValue) {
        val isOpen = drawerState.targetValue == DrawerValue.Open
        if (isOpen != uiState.drawerOpen) {
            if (isOpen) onToggleDrawer() else onCloseDrawer()
        }
    }

    // Sync drawer FROM ViewModel → UI
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
                onNavigateToLottery = onNavigateToLottery
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
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                AnimatedVisibility(visible = uiState.error != null) {
                    ErrorBanner(uiState.error ?: "", onDismissError)
                }

                ChatMessages(messages = uiState.messages, isThinking = uiState.isThinking,
                    language = uiState.language, speakingMessageId = uiState.speakingMessageId,
                    onSpeakMessage = onSpeakMessage, onCopyMessage = onCopyMessage,
                    onExportMessage = onExportMessage, modifier = Modifier.weight(1f))

                InputBar(text = uiState.inputText, language = uiState.language,
                    isListening = uiState.isListening, isSpeaking = uiState.isSpeaking,
                    pendingAttachment = uiState.pendingAttachment, onTextChange = onInputChange,
                    onSend = onSend, onStartListening = onStartListening,
                    onStopListening = onStopListening, onStopSpeaking = onStopSpeaking,
                    onAttachFile = onAttachFile, onClearAttachment = onClearAttachment)
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
    onSetDrawerView: (Int) -> Unit, onNavigateToLottery: () -> Unit = {}
) {
    val sessions = uiState.sessions
    val activeSessionId = uiState.activeSessionId
    val user = uiState.user
    val lang = uiState.language

    ModalDrawerSheet(modifier = Modifier.width(300.dp), drawerContainerColor = MaterialTheme.colorScheme.surface) {
        // Header
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.radialGradient(listOf(NexaAccent.copy(alpha = 0.15f), NexaAccent.copy(alpha = 0.03f)))),
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

        // Session list
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(sessions, key = { it.id }) { session ->
                ChatSessionItem(session = session, language = lang, isActive = session.id == activeSessionId,
                    onClick = { onSwitchSession(session.id) }, onDelete = { onDeleteSession(session.id) })
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
                Icon(if (user.isLoggedIn) Icons.Default.Logout else Icons.Default.Person, contentDescription = null,
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
fun ChatSessionItem(session: ChatSession, language: AppLanguage, isActive: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
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
                    DropdownMenuItem(text = { Text("🗑️ ${NexaStrings.get("delete_chat", language)}") },
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
            IconButton(onClick = onToggleSettings) {
                Icon(Icons.Default.Settings, contentDescription = NexaStrings.get("settings", uiState.language),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }
            if (uiState.isSpeaking) {
                IconButton(onClick = onStopSpeaking) {
                    Icon(Icons.Default.StopCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                }
            }
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

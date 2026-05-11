package com.nexa.ai.ui

import androidx.compose.animation.*
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.viewmodel.*
import kotlinx.coroutines.launch

// ═══════════════════════════════════════
//  MAIN SCREEN
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexaChatScreen(
    uiState: NexaUiState,
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
    onToggleTheme: () -> Unit
) {
    val drawerState = rememberDrawerState(
        initialValue = if (uiState.drawerOpen) DrawerValue.Open else DrawerValue.Closed
    )
    val coroutineScope = rememberCoroutineScope()

    // Sync drawer state with uiState
    LaunchedEffect(uiState.drawerOpen) {
        if (uiState.drawerOpen) drawerState.open() else drawerState.close()
    }

    // Sync uiState when drawer is swiped
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open && !uiState.drawerOpen) {
            onToggleDrawer()
        } else if (drawerState.currentValue == DrawerValue.Closed && uiState.drawerOpen) {
            onCloseDrawer()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                sessions = uiState.sessions,
                activeSessionId = uiState.activeSessionId,
                onNewChat = onCreateSession,
                onSwitchSession = onSwitchSession,
                onDeleteSession = onDeleteSession,
                onClose = { coroutineScope.launch { drawerState.close() } }
            )
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    uiState = uiState,
                    onToggleDrawer = onToggleDrawer,
                    onToggleAutoSpeak = onToggleAutoSpeak,
                    onStopSpeaking = onStopSpeaking,
                    onClearChat = onClearChat
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Error banner
                AnimatedVisibility(visible = uiState.error != null) {
                    ErrorBanner(uiState.error ?: "", onDismissError)
                }

                // Messages
                ChatMessages(
                    messages = uiState.messages,
                    isThinking = uiState.isThinking,
                    speakingMessageId = uiState.speakingMessageId,
                    onSpeakMessage = onSpeakMessage,
                    modifier = Modifier.weight(1f)
                )

                // Bottom settings bar
                BottomSettingsBar(
                    uiState = uiState,
                    onSetLanguage = onSetLanguage,
                    onSetVoiceType = onSetVoiceType,
                    onToggleTheme = onToggleTheme,
                    onToggleSettings = onToggleSettings
                )

                // Input bar
                InputBar(
                    text = uiState.inputText,
                    isListening = uiState.isListening,
                    isSpeaking = uiState.isSpeaking,
                    onTextChange = onInputChange,
                    onSend = onSend,
                    onStartListening = onStartListening,
                    onStopListening = onStopListening,
                    onStopSpeaking = onStopSpeaking
                )
            }
        }
    }

    // Settings sheet
    if (uiState.showSettings) {
        SettingsSheet(
            uiState = uiState,
            onDismiss = onToggleSettings,
            onSetLanguage = onSetLanguage,
            onSetVoiceType = onSetVoiceType,
            onToggleTheme = onToggleTheme,
            onToggleAutoSpeak = onToggleAutoSpeak
        )
    }
}

// ═══════════════════════════════════════
//  DRAWER
// ═══════════════════════════════════════

@Composable
fun DrawerContent(
    sessions: List<ChatSession>,
    activeSessionId: String?,
    onNewChat: () -> Unit,
    onSwitchSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NexaAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚡", fontSize = 20.sp)
                }
                Column {
                    Text("NEXA AI", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 1.sp)
                    Text(
                        "Historial de chats",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // New chat button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { onNewChat() },
            shape = RoundedCornerShape(12.dp),
            color = NexaAccent.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo", tint = NexaAccent, modifier = Modifier.size(20.dp))
                Text("Nuevo chat", color = NexaAccent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chat list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            items(sessions, key = { it.id }) { session ->
                ChatSessionItem(
                    session = session,
                    isActive = session.id == activeSessionId,
                    onClick = { onSwitchSession(session.id) },
                    onDelete = { onDeleteSession(session.id) }
                )
            }
        }

        // Bottom: app version
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "NEXA AI v2.0",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ChatSessionItem(
    session: ChatSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) NexaAccent.copy(alpha = 0.08f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isActive) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.title,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) NexaAccent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${session.messages.size} mensajes",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Three dots menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("🗑️ Borrar chat") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
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
fun ChatTopBar(
    uiState: NexaUiState,
    onToggleDrawer: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
    onClearChat: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NexaAccent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚡", fontSize = 16.sp)
                }
                Column {
                    Text("NEXA", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 1.5.sp)
                    Text(
                        "ANDROID",
                        fontSize = 8.sp,
                        color = NexaAccent,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        navigationIcon = {
            // Hamburger menu for drawer
            IconButton(onClick = onToggleDrawer) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Menú",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            // Auto-speak toggle
            IconButton(onClick = onToggleAutoSpeak) {
                Icon(
                    if (uiState.autoSpeak) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = if (uiState.autoSpeak) "Desactivar voz" else "Activar voz",
                    tint = if (uiState.autoSpeak) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Stop speaking
            if (uiState.isSpeaking) {
                IconButton(onClick = onStopSpeaking) {
                    Icon(
                        Icons.Default.StopCircle,
                        contentDescription = "Detener lectura",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            // Clear chat
            IconButton(onClick = onClearChat) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Limpiar chat")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

// ═══════════════════════════════════════
//  ERROR BANNER
// ═══════════════════════════════════════

@Composable
fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "❌ $error",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ═══════════════════════════════════════
//  MESSAGES
// ═══════════════════════════════════════

@Composable
fun ChatMessages(
    messages: List<Message>,
    isThinking: Boolean,
    speakingMessageId: String?,
    onSpeakMessage: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (messages.isEmpty()) {
            item { EmptyState() }
        }

        items(messages, key = { it.id }) { msg ->
            MessageBubble(
                message = msg,
                isSpeaking = speakingMessageId == msg.id,
                onSpeak = { onSpeakMessage(msg.content, msg.id) }
            )
        }

        if (isThinking) {
            item { ThinkingIndicator() }
        }
    }
}

// ═══════════════════════════════════════
//  EMPTY STATE
// ═══════════════════════════════════════

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🧬", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("NEXA AI", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Toca el micrófono y habla,\no escribe tu mensaje.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("¿Qué puedes hacer?", "Escribe un poema", "Cuéntame un chiste").forEach { suggestion ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = NexaAccent.copy(alpha = 0.1f),
                    border = ButtonDefaults.outlinedButtonBorder
                ) {
                    Text(
                        suggestion,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = NexaAccent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
//  MESSAGE BUBBLE
// ═══════════════════════════════════════

@Composable
fun MessageBubble(
    message: Message,
    isSpeaking: Boolean,
    onSpeak: () -> Unit
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 20.dp
            ),
            color = if (isUser) NexaAccent.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.linearGradient(
                    if (isUser) listOf(NexaAccent.copy(0.2f), NexaAccent.copy(0.2f))
                    else listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outline)
                )
            ),
            shadowElevation = if (!isUser) 2.dp else 0.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (message.isStreaming && message.content.isEmpty()) {
                    DotsTyping()
                } else {
                    Text(
                        text = message.content,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (!isUser && !message.isStreaming && message.content.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                        contentDescription = if (isSpeaking) "Detener" else "Leer",
                        modifier = Modifier.size(16.dp),
                        tint = if (isSpeaking) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
//  THINKING INDICATOR
// ═══════════════════════════════════════

@Composable
fun ThinkingIndicator() {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(NexaAccent)
            )
        }
        Text("pensando...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DotsTyping() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(NexaAccent)
            )
        }
    }
}

// ═══════════════════════════════════════
//  INPUT BAR
// ═══════════════════════════════════════

@Composable
fun InputBar(
    text: String,
    isListening: Boolean,
    isSpeaking: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Text input
                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        placeholder = {
                            Text(
                                if (isListening) "🎙️ Escuchando..." else "Escribe un mensaje...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            onSend()
                            keyboardController?.hide()
                        }),
                        maxLines = 4
                    )

                    // Mic button — prominent
                    IconButton(
                        onClick = { if (isListening) onStopListening() else onStartListening() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                else NexaAccent.copy(alpha = 0.1f)
                            )
                    ) {
                        Icon(
                            if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isListening) "Detener" else "Hablar",
                            tint = if (isListening) MaterialTheme.colorScheme.error else NexaAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Stop TTS button (visible when speaking)
                    if (isSpeaking) {
                        IconButton(
                            onClick = onStopSpeaking,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                Icons.Default.StopCircle,
                                contentDescription = "Detener lectura",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Send button
                    IconButton(
                        onClick = {
                            onSend()
                            keyboardController?.hide()
                        },
                        enabled = text.isNotBlank(),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (text.isNotBlank()) NexaAccent
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar",
                            tint = if (text.isNotBlank()) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════
//  BOTTOM SETTINGS BAR
// ═══════════════════════════════════════

@Composable
fun BottomSettingsBar(
    uiState: NexaUiState,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetVoiceType: (VoiceType) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Login
            BottomSettingItem(
                icon = Icons.Default.Person,
                label = "Login",
                onClick = { /* TODO: Login screen */ }
            )

            // Settings
            BottomSettingItem(
                icon = Icons.Default.Settings,
                label = "Ajustes",
                onClick = onToggleSettings
            )

            // Language
            BottomSettingItem(
                icon = Icons.Default.Language,
                label = uiState.language.label,
                onClick = {
                    val next = if (uiState.language == AppLanguage.SPANISH) AppLanguage.ENGLISH else AppLanguage.SPANISH
                    onSetLanguage(next)
                }
            )

            // Voice
            BottomSettingItem(
                icon = if (uiState.voiceType == VoiceType.MALE) Icons.Default.Man else Icons.Default.Woman,
                label = if (uiState.voiceType == VoiceType.MALE) "Hombre" else "Mujer",
                onClick = {
                    val next = if (uiState.voiceType == VoiceType.MALE) VoiceType.FEMALE else VoiceType.MALE
                    onSetVoiceType(next)
                }
            )

            // Theme
            BottomSettingItem(
                icon = if (uiState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                label = if (uiState.isDarkTheme) "Oscuro" else "Claro",
                onClick = onToggleTheme
            )
        }
    }
}

@Composable
fun BottomSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════
//  SETTINGS SHEET
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    uiState: NexaUiState,
    onDismiss: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetVoiceType: (VoiceType) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleAutoSpeak: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                "⚙️ Ajustes",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Theme
            SettingRow(
                title = "Tema",
                subtitle = if (uiState.isDarkTheme) "Oscuro" else "Claro"
            ) {
                Switch(
                    checked = uiState.isDarkTheme,
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(checkedTrackColor = NexaAccent)
                )
            }

            // Language
            SettingRow(
                title = "Idioma",
                subtitle = uiState.language.label
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = uiState.language == lang,
                            onClick = { onSetLanguage(lang) },
                            label = { Text(lang.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NexaAccent.copy(alpha = 0.15f),
                                selectedLabelColor = NexaAccent
                            )
                        )
                    }
                }
            }

            // Voice
            SettingRow(
                title = "Voz",
                subtitle = if (uiState.voiceType == VoiceType.MALE) "Hombre" else "Mujer"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceType.entries.forEach { voice ->
                        FilterChip(
                            selected = uiState.voiceType == voice,
                            onClick = { onSetVoiceType(voice) },
                            label = {
                                Text(
                                    if (voice == VoiceType.MALE) "👨 Hombre" else "👩 Mujer",
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NexaAccent.copy(alpha = 0.15f),
                                selectedLabelColor = NexaAccent
                            )
                        )
                    }
                }
            }

            // Auto-speak
            SettingRow(
                title = "Lectura automática",
                subtitle = if (uiState.autoSpeak) "NEXA habla las respuestas" else "Solo texto"
            ) {
                Switch(
                    checked = uiState.autoSpeak,
                    onCheckedChange = { onToggleAutoSpeak() },
                    colors = SwitchDefaults.colors(checkedTrackColor = NexaAccent)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

package com.nexa.ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.viewmodel.*
import kotlinx.coroutines.launch

// ═══════════════════════════════════════
//  MAIN SCREEN WITH NAVIGATION
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
    onToggleTheme: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToChat: () -> Unit,
    onUpdateLoginEmail: (String) -> Unit,
    onUpdateLoginPassword: (String) -> Unit,
    onLogin: () -> Unit,
    onUpdateRegisterName: (String) -> Unit,
    onUpdateRegisterEmail: (String) -> Unit,
    onUpdateRegisterPassword: (String) -> Unit,
    onUpdateRegisterConfirmPassword: (String) -> Unit,
    onRegister: () -> Unit,
    onLogout: () -> Unit,
    onDismissUpdate: () -> Unit,
    onOpenUpdatePage: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onExportMessage: (Message) -> Unit,
    onSurpriseMe: () -> Unit,
    onSetDrawerView: (Int) -> Unit,
    onAttachFile: () -> Unit,
    onClearAttachment: () -> Unit = {}
) {
    if (uiState.showUpdateDialog && uiState.updateInfo != null) {
        UpdateDialog(
            updateInfo = uiState.updateInfo,
            onDismiss = onDismissUpdate,
            onUpdate = onOpenUpdatePage,
            language = uiState.language
        )
    }

    when (uiState.currentScreen) {
        Screen.LOGIN -> LoginScreen(
            email = uiState.loginEmail,
            password = uiState.loginPassword,
            error = uiState.loginError,
            isLoading = uiState.isLoggingIn,
            onEmailChange = onUpdateLoginEmail,
            onPasswordChange = onUpdateLoginPassword,
            onLogin = onLogin,
            onGoToRegister = onNavigateToRegister,
            onBack = onNavigateToChat,
            isDarkTheme = uiState.isDarkTheme,
            language = uiState.language
        )
        Screen.REGISTER -> RegisterScreen(
            name = uiState.registerName,
            email = uiState.registerEmail,
            password = uiState.registerPassword,
            confirmPassword = uiState.registerConfirmPassword,
            error = uiState.registerError,
            isLoading = uiState.isRegistering,
            onNameChange = onUpdateRegisterName,
            onEmailChange = onUpdateRegisterEmail,
            onPasswordChange = onUpdateRegisterPassword,
            onConfirmPasswordChange = onUpdateRegisterConfirmPassword,
            onRegister = onRegister,
            onGoToLogin = onNavigateToLogin,
            onBack = onNavigateToChat,
            isDarkTheme = uiState.isDarkTheme,
            language = uiState.language
        )
        Screen.CHAT -> ChatMainScreen(
            uiState = uiState,
            onSend = onSend,
            onInputChange = onInputChange,
            onStartListening = onStartListening,
            onStopListening = onStopListening,
            onToggleAutoSpeak = onToggleAutoSpeak,
            onStopSpeaking = onStopSpeaking,
            onSpeakMessage = onSpeakMessage,
            onClearChat = onClearChat,
            onDismissError = onDismissError,
            onToggleDrawer = onToggleDrawer,
            onCloseDrawer = onCloseDrawer,
            onCreateSession = onCreateSession,
            onSwitchSession = onSwitchSession,
            onDeleteSession = onDeleteSession,
            onToggleSettings = onToggleSettings,
            onSetLanguage = onSetLanguage,
            onSetVoiceType = onSetVoiceType,
            onToggleTheme = onToggleTheme,
            onNavigateToLogin = onNavigateToLogin,
            onLogout = onLogout,
            onCopyMessage = onCopyMessage,
            onExportMessage = onExportMessage,
            onSurpriseMe = onSurpriseMe,
            onSetDrawerView = onSetDrawerView,
            onAttachFile = onAttachFile,
            onClearAttachment = onClearAttachment
        )
    }

    if (uiState.showSettings) {
        GeneralSettingsDialog(
            uiState = uiState,
            onDismiss = onToggleSettings,
            onSetLanguage = onSetLanguage,
            onSetVoiceType = onSetVoiceType,
            onToggleTheme = onToggleTheme,
            onToggleAutoSpeak = onToggleAutoSpeak,
            onClearChat = onClearChat,
            onNavigateToLogin = onNavigateToLogin,
            onLogout = onLogout
        )
    }
}

// ═══════════════════════════════════════
//  LOGIN SCREEN
// ═══════════════════════════════════════

@Composable
fun LoginScreen(
    email: String,
    password: String,
    error: String?,
    isLoading: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onGoToRegister: () -> Unit,
    onBack: () -> Unit,
    isDarkTheme: Boolean,
    language: AppLanguage
) {
    var showPassword by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDarkTheme) Color(0xFF0A0A0A) else Color(0xFFF5F5F5)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = NexaStrings.get("back", language),
                        tint = if (isDarkTheme) Color.White else Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(NexaAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 36.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("NEXA PRO", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(
                NexaStrings.get("login_title", language),
                fontSize = 14.sp,
                color = if (isDarkTheme) Color(0xFF888888) else Color(0xFF666666),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text(NexaStrings.get("email", language)) },
                placeholder = { Text("tu@email.com") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NexaAccent,
                    focusedLabelColor = NexaAccent,
                    cursorColor = NexaAccent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(NexaStrings.get("password", language)) },
                placeholder = { Text("••••••••") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    onLogin()
                    keyboardController?.hide()
                }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NexaAccent,
                    focusedLabelColor = NexaAccent,
                    cursorColor = NexaAccent
                )
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    onLogin()
                    keyboardController?.hide()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NexaAccent),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        NexaStrings.get("login", language),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFDDDDDD))
                Text("  o  ", fontSize = 12.sp, color = if (isDarkTheme) Color(0xFF666666) else Color(0xFF999999))
                HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFDDDDDD))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    NexaStrings.get("no_account", language) + " ",
                    fontSize = 14.sp,
                    color = if (isDarkTheme) Color(0xFF888888) else Color(0xFF666666)
                )
                Text(
                    NexaStrings.get("register", language),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexaAccent,
                    modifier = Modifier.clickable { onGoToRegister() }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ═══════════════════════════════════════
//  REGISTER SCREEN
// ═══════════════════════════════════════

@Composable
fun RegisterScreen(
    name: String,
    email: String,
    password: String,
    confirmPassword: String,
    error: String?,
    isLoading: Boolean,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onRegister: () -> Unit,
    onGoToLogin: () -> Unit,
    onBack: () -> Unit,
    isDarkTheme: Boolean,
    language: AppLanguage
) {
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDarkTheme) Color(0xFF0A0A0A) else Color(0xFFF5F5F5)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = NexaStrings.get("back", language),
                        tint = if (isDarkTheme) Color.White else Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(NexaAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 36.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("NEXA PRO", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(
                NexaStrings.get("create_account", language),
                fontSize = 14.sp,
                color = if (isDarkTheme) Color(0xFF888888) else Color(0xFF666666),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(NexaStrings.get("name", language)) },
                placeholder = { Text(NexaStrings.get("your_name", language)) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NexaAccent,
                    focusedLabelColor = NexaAccent,
                    cursorColor = NexaAccent
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text(NexaStrings.get("email", language)) },
                placeholder = { Text("tu@email.com") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NexaAccent,
                    focusedLabelColor = NexaAccent,
                    cursorColor = NexaAccent
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(NexaStrings.get("password", language)) },
                placeholder = { Text(NexaStrings.get("min_6", language)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NexaAccent,
                    focusedLabelColor = NexaAccent,
                    cursorColor = NexaAccent
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text(NexaStrings.get("confirm_password", language)) },
                placeholder = { Text(NexaStrings.get("repeat_password", language)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showConfirm = !showConfirm }) {
                        Icon(
                            if (showConfirm) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    onRegister()
                    keyboardController?.hide()
                }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NexaAccent,
                    focusedLabelColor = NexaAccent,
                    cursorColor = NexaAccent
                )
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    onRegister()
                    keyboardController?.hide()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NexaAccent),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        NexaStrings.get("create_account_btn", language),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    NexaStrings.get("has_account", language) + " ",
                    fontSize = 14.sp,
                    color = if (isDarkTheme) Color(0xFF888888) else Color(0xFF666666)
                )
                Text(
                    NexaStrings.get("login", language),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexaAccent,
                    modifier = Modifier.clickable { onGoToLogin() }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ═══════════════════════════════════════
//  CHAT MAIN SCREEN
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMainScreen(
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
    onToggleTheme: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onLogout: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onExportMessage: (Message) -> Unit,
    onSurpriseMe: () -> Unit,
    onSetDrawerView: (Int) -> Unit,
    onAttachFile: () -> Unit,
    onClearAttachment: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )
    val coroutineScope = rememberCoroutineScope()

    // Sync drawer FROM UI → ViewModel (user swiped)
    LaunchedEffect(drawerState.targetValue) {
        val isOpen = drawerState.targetValue == DrawerValue.Open
        if (isOpen != uiState.drawerOpen) {
            if (isOpen) onToggleDrawer() else onCloseDrawer()
        }
    }

    // Sync drawer FROM ViewModel → UI (programmatic)
    LaunchedEffect(uiState.drawerOpen) {
        if (uiState.drawerOpen && drawerState.isClosed) drawerState.open()
        else if (!uiState.drawerOpen && drawerState.isOpen) drawerState.close()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                uiState = uiState,
                onNewChat = onCreateSession,
                onSwitchSession = onSwitchSession,
                onDeleteSession = onDeleteSession,
                onClose = { coroutineScope.launch { drawerState.close() } },
                onNavigateToLogin = onNavigateToLogin,
                onLogout = onLogout,
                onSetLanguage = onSetLanguage,
                onSetVoiceType = onSetVoiceType,
                onToggleTheme = onToggleTheme,
                onToggleSettings = onToggleSettings,
                onToggleAutoSpeak = onToggleAutoSpeak,
                onSetDrawerView = onSetDrawerView
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
                    onClearChat = onClearChat,
                    onSurpriseMe = onSurpriseMe,
                    onToggleSettings = onToggleSettings
                )
            },
            containerColor = if (uiState.isDarkTheme) Color.Black else Color.White
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AnimatedVisibility(visible = uiState.error != null) {
                    ErrorBanner(uiState.error ?: "", onDismissError)
                }

                ChatMessages(
                    messages = uiState.messages,
                    isThinking = uiState.isThinking,
                    language = uiState.language,
                    speakingMessageId = uiState.speakingMessageId,
                    onSpeakMessage = onSpeakMessage,
                    onCopyMessage = onCopyMessage,
                    onExportMessage = onExportMessage,
                    modifier = Modifier.weight(1f)
                )

                InputBar(
                    text = uiState.inputText,
                    language = uiState.language,
                    isListening = uiState.isListening,
                    isSpeaking = uiState.isSpeaking,
                    pendingAttachment = uiState.pendingAttachment,
                    onTextChange = onInputChange,
                    onSend = onSend,
                    onStartListening = onStartListening,
                    onStopListening = onStopListening,
                    onStopSpeaking = onStopSpeaking,
                    onAttachFile = onAttachFile,
                    onClearAttachment = onClearAttachment
                )
            }
        }
    }
}

// ═══════════════════════════════════════
//  DRAWER — History + Quick Settings
// ═══════════════════════════════════════

@Composable
fun DrawerContent(
    uiState: NexaUiState,
    onNewChat: () -> Unit,
    onSwitchSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClose: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onLogout: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetVoiceType: (VoiceType) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleSettings: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onSetDrawerView: (Int) -> Unit
) {
    val sessions = uiState.sessions
    val activeSessionId = uiState.activeSessionId
    val user = uiState.user
    val lang = uiState.language

    ModalDrawerSheet(
        modifier = Modifier.width(310.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexaAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚡", fontSize = 22.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "NEXA PRO",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        letterSpacing = 1.5.sp
                    )
                    if (user.isLoggedIn) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(NexaAccent)
                            )
                            Text(
                                user.displayName,
                                fontSize = 11.sp,
                                color = NexaAccent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // New chat button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clickable { onNewChat() },
            shape = RoundedCornerShape(14.dp),
            color = NexaAccent.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = NexaAccent, modifier = Modifier.size(20.dp))
                Text(
                    NexaStrings.get("new_chat", lang),
                    color = NexaAccent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Chat history
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            items(sessions, key = { it.id }) { session ->
                ChatSessionItem(
                    session = session,
                    language = lang,
                    isActive = session.id == activeSessionId,
                    onClick = { onSwitchSession(session.id) },
                    onDelete = { onDeleteSession(session.id) }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        // === Quick Settings ===
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Language selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val next = if (lang == AppLanguage.SPANISH) AppLanguage.ENGLISH else AppLanguage.SPANISH
                        onSetLanguage(next)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NexaAccent.copy(alpha = 0.12f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Language, null, modifier = Modifier.size(16.dp), tint = NexaAccent)
                    }
                }
                Text(NexaStrings.get("language", lang), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Spanish flag
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (lang == AppLanguage.SPANISH) NexaAccent.copy(alpha = 0.15f) else Color.Transparent,
                        border = if (lang == AppLanguage.SPANISH) BorderStroke(1.dp, NexaAccent.copy(alpha = 0.4f)) else null,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onSetLanguage(AppLanguage.SPANISH) }
                    ) {
                        Text("\uD83C\uDDEA\uD83C\uDDF8", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    // English flag
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (lang == AppLanguage.ENGLISH) NexaAccent.copy(alpha = 0.15f) else Color.Transparent,
                        border = if (lang == AppLanguage.ENGLISH) BorderStroke(1.dp, NexaAccent.copy(alpha = 0.4f)) else null,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onSetLanguage(AppLanguage.ENGLISH) }
                    ) {
                        Text("\uD83C\uDDFA\uD83C\uDDF8", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            // Voice selector
            var showVoicePicker by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showVoicePicker = !showVoicePicker }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NexaAccent.copy(alpha = 0.12f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val isMale = uiState.voiceType == VoiceType.MALE_1 || uiState.voiceType == VoiceType.MALE_2 || uiState.voiceType == VoiceType.MALE_3
                        Icon(if (isMale) Icons.Default.Man else Icons.Default.Woman, null, modifier = Modifier.size(16.dp), tint = NexaAccent)
                    }
                }
                Text(NexaStrings.get("voice", lang), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(NexaStrings.get(uiState.voiceType.name.lowercase(), lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (showVoicePicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Voice picker grid (expandable)
            AnimatedVisibility(visible = showVoicePicker) {
                Column(modifier = Modifier.padding(start = 40.dp, end = 4.dp, bottom = 4.dp)) {
                    Text(
                        if (lang == AppLanguage.SPANISH) "Mujer" else "Female",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NexaAccent.copy(alpha = 0.6f), letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(VoiceType.FEMALE_1, VoiceType.FEMALE_2, VoiceType.FEMALE_3).forEach { voice ->
                            val selected = uiState.voiceType == voice
                            Surface(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onSetVoiceType(voice) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) NexaAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = if (selected) BorderStroke(1.dp, NexaAccent.copy(alpha = 0.5f)) else null
                            ) {
                                Column(modifier = Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("👩", fontSize = 14.sp)
                                    Text(NexaStrings.get(voice.name.lowercase(), lang), fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) NexaAccent else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (lang == AppLanguage.SPANISH) "Hombre" else "Male",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NexaAccent.copy(alpha = 0.6f), letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(VoiceType.MALE_1, VoiceType.MALE_2, VoiceType.MALE_3).forEach { voice ->
                            val selected = uiState.voiceType == voice
                            Surface(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onSetVoiceType(voice) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) NexaAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = if (selected) BorderStroke(1.dp, NexaAccent.copy(alpha = 0.5f)) else null
                            ) {
                                Column(modifier = Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("👨", fontSize = 14.sp)
                                    Text(NexaStrings.get(voice.name.lowercase(), lang), fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) NexaAccent else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }

            // Theme toggle
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onToggleTheme() }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = NexaAccent.copy(alpha = 0.12f), modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (uiState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode, null, modifier = Modifier.size(16.dp), tint = NexaAccent)
                    }
                }
                Text(NexaStrings.get("theme", lang), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(false to Icons.Default.LightMode, true to Icons.Default.DarkMode).forEach { (dark, icon) ->
                        val selected = uiState.isDarkTheme == dark
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (selected) NexaAccent.copy(alpha = 0.15f) else Color.Transparent,
                            border = if (selected) BorderStroke(1.dp, NexaAccent.copy(alpha = 0.4f)) else null,
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable { if (!selected) onToggleTheme() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, null, modifier = Modifier.size(14.dp), tint = if (selected) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Auto-speak toggle
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onToggleAutoSpeak() }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = NexaAccent.copy(alpha = 0.12f), modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (uiState.autoSpeak) Icons.Default.VolumeUp else Icons.Default.VolumeOff, null, modifier = Modifier.size(16.dp), tint = NexaAccent)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(NexaStrings.get("auto_speak", lang), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(NexaStrings.get("auto_speak_desc", lang), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                Switch(
                    checked = uiState.autoSpeak,
                    onCheckedChange = { onToggleAutoSpeak() },
                    colors = SwitchDefaults.colors(checkedTrackColor = NexaAccent),
                    modifier = Modifier.scale(0.75f)
                )
            }
        }

        // Account
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { if (user.isLoggedIn) onLogout() else onNavigateToLogin() }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (user.isLoggedIn) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else NexaAccent.copy(alpha = 0.12f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (user.isLoggedIn) Icons.Default.Logout else Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = if (user.isLoggedIn) MaterialTheme.colorScheme.error else NexaAccent)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (user.isLoggedIn) NexaStrings.get("logout", lang) else NexaStrings.get("login", lang),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = if (user.isLoggedIn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (user.isLoggedIn) {
                    Text(user.email, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Settings gear — absolute bottom
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            IconButton(
                onClick = {
                    onToggleSettings()
                    onClose()
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = NexaStrings.get("settings", lang),
                    tint = NexaAccent.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ═══════════════════════════════════════
//  SETTINGS PANEL (inside drawer)
// ═══════════════════════════════════════

@Composable
fun SettingsPanel(
    uiState: NexaUiState,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetVoiceType: (VoiceType) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val lang = uiState.language
    val user = uiState.user

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
    ) {
        Text(NexaStrings.get("general", lang), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexaAccent.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 12.dp))

        UltraSettingRow(icon = Icons.Default.DarkMode, title = NexaStrings.get("theme", lang)) {
            Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Icons.Default.LightMode, Icons.Default.DarkMode).forEach { icon ->
                    val selected = if (icon == Icons.Default.DarkMode) uiState.isDarkTheme else !uiState.isDarkTheme
                    Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(if (selected) NexaAccent else Color.Transparent).clickable { if (!selected) onToggleTheme() }, contentAlignment = Alignment.Center) {
                        Icon(icon, null, modifier = Modifier.size(14.dp), tint = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        UltraSettingRow(icon = Icons.Default.Language, title = NexaStrings.get("language", lang), value = uiState.language.label, onClick = {
            val next = if (uiState.language == AppLanguage.SPANISH) AppLanguage.ENGLISH else AppLanguage.SPANISH
            onSetLanguage(next)
        })

        UltraSettingRow(
            icon = if (uiState.voiceType == VoiceType.MALE_1 || uiState.voiceType == VoiceType.MALE_2 || uiState.voiceType == VoiceType.MALE_3) Icons.Default.Man else Icons.Default.Woman,
            title = NexaStrings.get("voice", lang),
            value = NexaStrings.get(uiState.voiceType.name.lowercase(), lang),
            onClick = {
                val voices = VoiceType.entries.toList()
                val nextIndex = (voices.indexOf(uiState.voiceType) + 1) % voices.size
                onSetVoiceType(voices[nextIndex])
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(90.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(VoiceType.entries.toList()) { voice ->
                val isMale = voice == VoiceType.MALE_1 || voice == VoiceType.MALE_2 || voice == VoiceType.MALE_3
                val selected = uiState.voiceType == voice
                Surface(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSetVoiceType(voice) }, shape = RoundedCornerShape(8.dp), color = if (selected) NexaAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), border = if (selected) BorderStroke(1.dp, NexaAccent) else null) {
                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isMale) "👨" else "👩", fontSize = 14.sp)
                        Text(NexaStrings.get(voice.name.lowercase(), lang), fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) NexaAccent else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(NexaStrings.get("interface_section", lang), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexaAccent.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 12.dp))

        UltraSettingRow(icon = if (uiState.autoSpeak) Icons.Default.VolumeUp else Icons.Default.VolumeOff, title = NexaStrings.get("auto_speak", lang)) {
            Switch(checked = uiState.autoSpeak, onCheckedChange = { onToggleAutoSpeak() }, colors = SwitchDefaults.colors(checkedTrackColor = NexaAccent), modifier = Modifier.scale(0.7f))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(NexaStrings.get("account_section", lang), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexaAccent.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 12.dp))

        UltraSettingRow(icon = if (user.isLoggedIn) Icons.Default.Person else Icons.Default.PersonOutline, title = if (user.isLoggedIn) user.email else NexaStrings.get("login", lang), value = if (user.isLoggedIn) NexaStrings.get("logout", lang) else null, onClick = {
            if (user.isLoggedIn) onLogout() else onNavigateToLogin()
        })

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ChatSessionItem(
    session: ChatSession,
    language: AppLanguage,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) NexaAccent.copy(alpha = 0.08f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (isActive) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(session.title, fontSize = 13.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal, color = if (isActive) NexaAccent else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${session.messages.size} ${NexaStrings.get("messages_count", language)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("🗑️ ${NexaStrings.get("delete_chat", language)}") }, onClick = { showMenu = false; onDelete() })
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
    onClearChat: () -> Unit,
    onSurpriseMe: () -> Unit,
    onToggleSettings: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Animated glow logo
                val infiniteTransition = rememberInfiniteTransition(label = "logo")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.15f, targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                    label = "glow"
                )
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(NexaAccent.copy(alpha = glowAlpha)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚡", fontSize = 18.sp)
                }
                Column {
                    Text("NEXA PRO", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, letterSpacing = 2.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(NexaAccent))
                        Text("EN LÍNEA", fontSize = 8.sp, color = NexaAccent, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onToggleDrawer) {
                Icon(Icons.Default.Menu, contentDescription = NexaStrings.get("menu", uiState.language), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            }
        },
        actions = {
            FilledIconButton(
                onClick = onSurpriseMe, modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = NexaAccent.copy(alpha = 0.12f), contentColor = NexaAccent),
                shape = RoundedCornerShape(10.dp)
            ) { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }

            FilledIconButton(
                onClick = onToggleAutoSpeak, modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (uiState.autoSpeak) NexaAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (uiState.autoSpeak) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) { Icon(if (uiState.autoSpeak) Icons.Default.VolumeUp else Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(18.dp)) }

            if (uiState.isSpeaking) {
                FilledIconButton(
                    onClick = onStopSpeaking, modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) { Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }

            FilledIconButton(
                onClick = onClearChat, modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) { Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = if (uiState.isDarkTheme) Color(0xFF0A0A0A) else Color.White)
    )
}

// ═══════════════════════════════════════
//  ERROR BANNER
// ═══════════════════════════════════════

@Composable
fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
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
    language: AppLanguage,
    speakingMessageId: String?,
    onSpeakMessage: (String, String) -> Unit,
    onCopyMessage: (String) -> Unit,
    onExportMessage: (Message) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (messages.isEmpty()) item { EmptyState(language) }
        items(messages, key = { it.id }) { msg ->
            MessageBubble(message = msg, isSpeaking = speakingMessageId == msg.id, language = language, onSpeak = { onSpeakMessage(msg.content, msg.id) }, onCopy = { onCopyMessage(msg.content) }, onExport = { onExportMessage(msg) })
        }
        if (isThinking) item { ThinkingIndicator(language) }
    }
}

@Composable
fun EmptyState(lang: AppLanguage) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "empty")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.97f, targetValue = 1.03f,
            animationSpec = infiniteRepeatable(animation = tween(3000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "pulse"
        )
        Box(
            modifier = Modifier.size((56 * scale).dp).clip(RoundedCornerShape(16.dp)).background(Brush.radialGradient(listOf(NexaAccent.copy(alpha = 0.18f), NexaAccent.copy(alpha = 0.04f)))),
            contentAlignment = Alignment.Center
        ) { Text("⚡", fontSize = 30.sp) }
        Spacer(modifier = Modifier.height(18.dp))
        Text("NEXA PRO", fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = NexaAccent.copy(alpha = 0.1f)) {
            Text("v3.1", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NexaAccent, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(NexaStrings.get("welcome_msg", lang), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 22.sp)
        Spacer(modifier = Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("🎙️", "💡", "⚡").forEach { emoji ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 16.sp) }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isSpeaking: Boolean,
    language: AppLanguage,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit
) {
    val isUser = message.role == "user"
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (isUser) 20.dp else 6.dp, bottomEnd = if (isUser) 6.dp else 20.dp),
            color = if (isUser) NexaAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shadowElevation = if (!isUser) 2.dp else 0.dp,
            border = if (!isUser) BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) else null
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // NEXA label inside the bubble for bot messages
                if (!isUser && !message.isStreaming && message.content.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(NexaAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("⚡", fontSize = 7.sp) }
                        Text("NEXA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NexaAccent.copy(alpha = 0.6f), letterSpacing = 1.sp)
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
            Row(modifier = Modifier.padding(top = 4.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = onSpeak, shape = RoundedCornerShape(8.dp), color = if (isSpeaking) NexaAccent.copy(alpha = 0.12f) else Color.Transparent, modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp, null, modifier = Modifier.size(15.dp), tint = if (isSpeaking) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
                }
                Surface(onClick = onCopy, shape = RoundedCornerShape(8.dp), color = Color.Transparent, modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
                }
                var showMsgMenu by remember { mutableStateOf(false) }
                Box {
                    Surface(onClick = { showMsgMenu = true }, shape = RoundedCornerShape(8.dp), color = Color.Transparent, modifier = Modifier.size(30.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.MoreHoriz, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
                    }
                    DropdownMenu(expanded = showMsgMenu, onDismissRequest = { showMsgMenu = false }) {
                        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp)); Text(NexaStrings.get("export_pdf", language)) } }, onClick = { showMsgMenu = false; onExport() })
                        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(18.dp)); Text(NexaStrings.get("read_aloud", language)) } }, onClick = { showMsgMenu = false; onSpeak() })
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingIndicator(lang: AppLanguage) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                val infiniteTransition = rememberInfiniteTransition(label = "dot$index")
                val alpha by infiniteTransition.animateFloat(initialValue = 0.2f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = index * 200), repeatMode = RepeatMode.Reverse), label = "dotAlpha$index")
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(NexaAccent.copy(alpha = alpha)))
            }
        }
        Text(NexaStrings.get("thinking", lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

@Composable
fun DotsTyping() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "typing$index")
            val alpha by infiniteTransition.animateFloat(initialValue = 0.2f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(500, delayMillis = index * 150), repeatMode = RepeatMode.Reverse), label = "typingAlpha$index")
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NexaAccent.copy(alpha = alpha)))
        }
    }
}

// ═══════════════════════════════════════
//  INPUT BAR
// ═══════════════════════════════════════

@Composable
fun InputBar(
    text: String,
    language: AppLanguage,
    isListening: Boolean,
    isSpeaking: Boolean,
    pendingAttachment: String?,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit,
    onAttachFile: () -> Unit,
    onClearAttachment: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var showMenu by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background, shadowElevation = 16.dp) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Attachment preview
            AnimatedVisibility(visible = pendingAttachment != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = NexaAccent.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = NexaAccent.copy(alpha = 0.15f), modifier = Modifier.size(32.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Attachment, null, modifier = Modifier.size(16.dp), tint = NexaAccent) } }
                        Text(pendingAttachment ?: "", fontSize = 13.sp, color = NexaAccent, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = onClearAttachment, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            // Main input row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Attach menu
                Box {
                    Surface(onClick = { showMenu = true }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Photo, null, modifier = Modifier.size(20.dp), tint = NexaAccent); Text(NexaStrings.get("upload_photo", language), fontSize = 14.sp) } }, onClick = { showMenu = false; onAttachFile() })
                        DropdownMenuItem(text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(20.dp), tint = NexaAccent); Text(NexaStrings.get("upload_pdf", language), fontSize = 14.sp) } }, onClick = { showMenu = false; onAttachFile() })
                    }
                }

                // Text input
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = text, onValueChange = onTextChange,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 42.dp),
                            placeholder = { Text(if (isListening) NexaStrings.get("listening", language) else NexaStrings.get("input_hint", language), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 14.sp) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend(); keyboardController?.hide() }),
                            maxLines = 4, textStyle = LocalTextStyle.current.copy(fontSize = 15.sp)
                        )
                        Surface(onClick = { if (isListening) onStopListening() else onStartListening() }, shape = CircleShape, color = if (isListening) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else Color.Transparent, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(if (isListening) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null, tint = if (isListening) MaterialTheme.colorScheme.error else NexaAccent, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }

                // Send button
                val canSend = text.isNotBlank() || pendingAttachment != null
                Surface(onClick = { onSend(); keyboardController?.hide() }, enabled = canSend, shape = CircleShape, color = if (canSend) NexaAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = NexaStrings.get("send", language), tint = if (canSend) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp)) }
                }
            }

            // Hint
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                Text(NexaStrings.get("mic_hint", language), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), letterSpacing = 0.5.sp)
            }
        }
    }
}

// ═══════════════════════════════════════
//  UPDATE DIALOG
// ═══════════════════════════════════════

@Composable
fun UpdateDialog(
    updateInfo: com.nexa.ai.data.UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    language: AppLanguage = AppLanguage.SPANISH
) {
    AlertDialog(
        onDismissRequest = { if (!updateInfo.forceUpdate) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🔄", fontSize = 24.sp)
                Text(NexaStrings.get("update_available", language), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text("v${updateInfo.versionName}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NexaAccent)
                Spacer(modifier = Modifier.height(8.dp))
                Text(updateInfo.changelog, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            }
        },
        confirmButton = { Button(onClick = onUpdate, colors = ButtonDefaults.buttonColors(containerColor = NexaAccent), shape = RoundedCornerShape(10.dp)) { Text(NexaStrings.get("update_now", language), color = Color.Black, fontWeight = FontWeight.Bold) } },
        dismissButton = { if (!updateInfo.forceUpdate) TextButton(onClick = onDismiss) { Text(NexaStrings.get("later", language), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        shape = RoundedCornerShape(18.dp)
    )
}

// ═══════════════════════════════════════
//  TRANSLATIONS
// ═══════════════════════════════════════

object NexaStrings {
    fun get(key: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.SPANISH -> spanish[key] ?: key
            AppLanguage.ENGLISH -> english[key] ?: key
        }
    }

    private val spanish = mapOf(
        "new_chat" to "Nuevo chat", "settings" to "Ajustes", "language" to "Idioma", "voice" to "Voz", "theme" to "Tema",
        "dark" to "Oscuro", "light" to "Claro", "male_1" to "Hombre 1", "male_2" to "Hombre 2", "male_3" to "Hombre 3",
        "female_1" to "Mujer 1", "female_2" to "Mujer 2", "female_3" to "Mujer 3", "login" to "Iniciar sesión",
        "logout" to "Cerrar sesión", "register" to "Registrarse", "email" to "Email", "password" to "Contraseña",
        "thinking" to "pensando...", "input_hint" to "Escribe un mensaje...", "listening" to "🎙️ Escuchando...",
        "mic_hint" to "🎙️ hablar • ↵ enviar", "messages_count" to "mensajes", "delete_chat" to "Borrar chat",
        "auto_speak" to "Lectura automática", "auto_speak_desc" to "NEXA habla las respuestas", "text_only" to "Solo texto",
        "welcome_msg" to "Toca el micrófono y habla,\no escribe tu mensaje.", "clear_chat" to "Limpiar chat",
        "attach" to "Adjuntar archivo", "send_img" to "Enviar imagen", "back" to "Volver",
        "update_available" to "Actualización disponible", "update_now" to "Actualizar", "later" to "Después",
        "menu" to "Menú", "surprise_me" to "Sorpréndeme", "disable_voice" to "Desactivar voz",
        "enable_voice" to "Activar voz", "stop" to "Detener", "history" to "Historial", "chats" to "chats",
        "general" to "GENERAL", "interface_section" to "INTERFAZ", "account_section" to "CUENTA",
        "upload_photo" to "Subir foto", "upload_pdf" to "Subir PDF", "send" to "Enviar", "export_pdf" to "Exportar PDF",
        "read_aloud" to "Leer en voz alta", "login_title" to "Inicia sesión", "create_account" to "Crea tu cuenta",
        "no_account" to "¿No tienes cuenta?", "has_account" to "¿Ya tienes cuenta?", "create_account_btn" to "Crear cuenta",
        "name" to "Nombre", "your_name" to "Tu nombre", "min_6" to "Mínimo 6 caracteres",
        "confirm_password" to "Confirmar contraseña", "repeat_password" to "Repite la contraseña",
        "fill_all" to "Completa todos los campos", "invalid_email" to "Email no válido", "min_chars" to "Mínimo 6 caracteres",
        "passwords_no_match" to "Las contraseñas no coinciden", "email_taken" to "Este email ya está registrado",
        "session_expired" to "Sesión expirada. Inicia sesión de nuevo.", "voice_unavailable" to "Reconocimiento de voz no disponible"
    )

    private val english = mapOf(
        "new_chat" to "New Chat", "settings" to "Settings", "language" to "Language", "voice" to "Voice", "theme" to "Theme",
        "dark" to "Dark", "light" to "Light", "male_1" to "Male 1", "male_2" to "Male 2", "male_3" to "Male 3",
        "female_1" to "Female 1", "female_2" to "Female 2", "female_3" to "Female 3", "login" to "Login",
        "logout" to "Logout", "register" to "Register", "email" to "Email", "password" to "Password",
        "thinking" to "thinking...", "input_hint" to "Type a message...", "listening" to "🎙️ Listening...",
        "mic_hint" to "🎙️ speak • ↵ send", "messages_count" to "messages", "delete_chat" to "Delete chat",
        "auto_speak" to "Auto-speak", "auto_speak_desc" to "NEXA speaks responses", "text_only" to "Text only",
        "welcome_msg" to "Tap the mic and speak,\nor type your message.", "clear_chat" to "Clear chat",
        "attach" to "Attach file", "send_img" to "Send image", "back" to "Back",
        "update_available" to "Update Available", "update_now" to "Update", "later" to "Later",
        "menu" to "Menu", "surprise_me" to "Surprise me", "disable_voice" to "Disable voice",
        "enable_voice" to "Enable voice", "stop" to "Stop", "history" to "History", "chats" to "chats",
        "general" to "GENERAL", "interface_section" to "INTERFACE", "account_section" to "ACCOUNT",
        "upload_photo" to "Upload photo", "upload_pdf" to "Upload PDF", "send" to "Send", "export_pdf" to "Export PDF",
        "read_aloud" to "Read aloud", "login_title" to "Sign in", "create_account" to "Create account",
        "no_account" to "Don't have an account?", "has_account" to "Already have an account?",
        "create_account_btn" to "Create account", "name" to "Name", "your_name" to "Your name",
        "min_6" to "Minimum 6 characters", "confirm_password" to "Confirm password", "repeat_password" to "Repeat password",
        "fill_all" to "Fill in all fields", "invalid_email" to "Invalid email", "min_chars" to "Minimum 6 characters",
        "passwords_no_match" to "Passwords don't match", "email_taken" to "This email is already registered",
        "session_expired" to "Session expired. Please sign in again.", "voice_unavailable" to "Voice recognition not available"
    )
}

@Composable
fun UltraSettingRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).let { if (onClick != null) it.clickable { onClick() } else it }.padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(16.dp), tint = NexaAccent) }
        Column(modifier = Modifier.weight(1f)) { Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) }
        if (value != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
        content?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsDialog(
    uiState: NexaUiState,
    onDismiss: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetVoiceType: (VoiceType) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onClearChat: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(NexaStrings.get("settings", uiState.language), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(NexaStrings.get("language", uiState.language), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AppLanguage.SPANISH to "Español 🇪🇸", AppLanguage.ENGLISH to "English 🇺🇸").forEach { (lang, label) ->
                        FilterChip(selected = uiState.language == lang, onClick = { onSetLanguage(lang) }, label = { Text(label) })
                    }
                }
                HorizontalDivider(color = if (uiState.isDarkTheme) Color(0xFF333333) else Color(0xFFEEEEEE))
                Text(NexaStrings.get("voice", uiState.language), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                val voices = VoiceType.entries.toList()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    voices.chunked(3).forEach { rowVoices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowVoices.forEach { voice ->
                                val isMale = voice.name.contains("MALE")
                                val selected = uiState.voiceType == voice
                                Surface(modifier = Modifier.weight(1f).clickable { onSetVoiceType(voice) }, shape = RoundedCornerShape(12.dp), color = if (selected) NexaAccent.copy(alpha = 0.2f) else (if (uiState.isDarkTheme) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)), border = if (selected) BorderStroke(2.dp, NexaAccent) else BorderStroke(1.dp, if (uiState.isDarkTheme) Color(0xFF333333) else Color(0xFFDDDDDD))) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(if (isMale) "👨" else "👩", fontSize = 16.sp)
                                        Text(NexaStrings.get(voice.name.lowercase(), uiState.language), fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) NexaAccent else (if (uiState.isDarkTheme) Color.White else Color.Black), textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = if (uiState.isDarkTheme) Color(0xFF333333) else Color(0xFFEEEEEE))
                Row(verticalAlignment = Alignment.CenterVertically) { Text(NexaStrings.get("auto_speak", uiState.language), modifier = Modifier.weight(1f)); Switch(checked = uiState.autoSpeak, onCheckedChange = { onToggleAutoSpeak() }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text(NexaStrings.get("theme", uiState.language), modifier = Modifier.weight(1f)); Switch(checked = uiState.isDarkTheme, onCheckedChange = { onToggleTheme() }) }
                HorizontalDivider(color = if (uiState.isDarkTheme) Color(0xFF333333) else Color(0xFFEEEEEE))
                Button(onClick = { onClearChat(); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.Delete, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text(NexaStrings.get("clear_chat", uiState.language)) }
                if (uiState.user.isLoggedIn) {
                    Button(onClick = { onLogout(); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = NexaAccent), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text(NexaStrings.get("logout", uiState.language)) }
                } else {
                    Button(onClick = { onNavigateToLogin(); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = NexaAccent, contentColor = Color.Black), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Person, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text(NexaStrings.get("login", uiState.language), fontWeight = FontWeight.Bold) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = NexaAccent) } },
        containerColor = if (uiState.isDarkTheme) Color(0xFF111111) else Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

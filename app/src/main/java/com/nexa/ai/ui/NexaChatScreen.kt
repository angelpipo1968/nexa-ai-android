package com.nexa.ai.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    onOpenUpdatePage: () -> Unit
) {
    // Update dialog
    if (uiState.showUpdateDialog && uiState.updateInfo != null) {
        UpdateDialog(
            updateInfo = uiState.updateInfo,
            onDismiss = onDismissUpdate,
            onUpdate = onOpenUpdatePage
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
            isDarkTheme = uiState.isDarkTheme
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
            isDarkTheme = uiState.isDarkTheme
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
    isDarkTheme: Boolean
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
            // Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = if (isDarkTheme) Color.White else Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Logo
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
                "Inicia sesión",
                fontSize = 14.sp,
                color = if (isDarkTheme) Color(0xFF888888) else Color(0xFF666666),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
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

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Contraseña") },
                placeholder = { Text("••••••••") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Ocultar" else "Mostrar"
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

            // Error
            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "❌ $error",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Login button
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
                        "Iniciar sesión",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFDDDDDD))
                Text("  o  ", fontSize = 12.sp, color = if (isDarkTheme) Color(0xFF666666) else Color(0xFF999999))
                HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFDDDDDD))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Register link
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "¿No tienes cuenta? ",
                    fontSize = 14.sp,
                    color = if (isDarkTheme) Color(0xFF888888) else Color(0xFF666666)
                )
                Text(
                    "Regístrate",
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
    isDarkTheme: Boolean
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
            // Back
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Volver",
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
                "Crea tu cuenta",
                fontSize = 14.sp,
                color = if (isDarkTheme) Color(0xFF888888) else Color(0xFF666666),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Nombre") },
                placeholder = { Text("Tu nombre") },
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

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
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

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Contraseña") },
                placeholder = { Text("Mínimo 6 caracteres") },
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

            // Confirm password
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text("Confirmar contraseña") },
                placeholder = { Text("Repite la contraseña") },
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
                    Text(
                        "❌ $error",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
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
                        "Crear cuenta",
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
                    "¿Ya tienes cuenta? ",
                    fontSize = 14.sp,
                    color = if (isDarkTheme) Color(0xFF888888) else Color(0xFF666666)
                )
                Text(
                    "Inicia sesión",
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
    onLogout: () -> Unit
) {
    val drawerState = rememberDrawerState(
        initialValue = if (uiState.drawerOpen) DrawerValue.Open else DrawerValue.Closed
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.drawerOpen) {
        if (uiState.drawerOpen) drawerState.open() else drawerState.close()
    }

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
                onToggleAutoSpeak = onToggleAutoSpeak
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
                AnimatedVisibility(visible = uiState.error != null) {
                    ErrorBanner(uiState.error ?: "", onDismissError)
                }

                ChatMessages(
                    messages = uiState.messages,
                    isThinking = uiState.isThinking,
                    language = uiState.language,
                    speakingMessageId = uiState.speakingMessageId,
                    onSpeakMessage = onSpeakMessage,
                    modifier = Modifier.weight(1f)
                )

                InputBar(
                    text = uiState.inputText,
                    language = uiState.language,
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

    if (uiState.showSettings) {
        // We will now handle settings inside the sidebar
    }
}

// ═══════════════════════════════════════
//  DRAWER
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
    onToggleAutoSpeak: () -> Unit
) {
    val sessions = uiState.sessions
    val activeSessionId = uiState.activeSessionId
    val user = uiState.user
    var showUserMenu by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(0) } // 0: History, 1: Settings

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        // User section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("NEXA PRO", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 1.sp)
                    if (user.isLoggedIn) {
                        Text(
                            user.displayName,
                            fontSize = 11.sp,
                            color = NexaAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Main Content Area
        AnimatedContent(
            targetState = viewMode,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            }
        ) { mode ->
            if (mode == 0) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // New chat
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
                            Text(NexaStrings.get("new_chat", uiState.language), color = NexaAccent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
                                language = uiState.language,
                                isActive = session.id == activeSessionId,
                                onClick = { onSwitchSession(session.id) },
                                onDelete = { onDeleteSession(session.id) }
                            )
                        }
                    }
                }
            } else {
                // NEXA ULTRA SETTINGS VIEW
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "GENERAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexaAccent.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    
                    UltraSettingRow(
                        icon = Icons.Default.DarkMode,
                        title = NexaStrings.get("theme", uiState.language)
                    ) {
                        // Segmented Theme Picker
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(Icons.Default.LightMode, Icons.Default.DarkMode).forEach { icon ->
                                val selected = if (icon == Icons.Default.DarkMode) uiState.isDarkTheme else !uiState.isDarkTheme
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) NexaAccent else Color.Transparent)
                                        .clickable { if (!selected) onToggleTheme() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, null, modifier = Modifier.size(14.dp), tint = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    UltraSettingRow(
                        icon = Icons.Default.Language,
                        title = NexaStrings.get("language", uiState.language),
                        value = uiState.language.label,
                        onClick = {
                            val next = if (uiState.language == AppLanguage.SPANISH) AppLanguage.ENGLISH else AppLanguage.SPANISH
                            onSetLanguage(next)
                        }
                    )

                    UltraSettingRow(
                        icon = if (uiState.voiceType == VoiceType.MALE_1 || uiState.voiceType == VoiceType.MALE_2) Icons.Default.Man else Icons.Default.Woman,
                        title = NexaStrings.get("voice", uiState.language),
                        value = NexaStrings.get(uiState.voiceType.name.lowercase(), uiState.language),
                        onClick = {
                            // Cycle through 4 voices
                            val voices = VoiceType.entries.toList()
                            val nextIndex = (voices.indexOf(uiState.voiceType) + 1) % voices.size
                            onSetVoiceType(voices[nextIndex])
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "INTERFAZ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexaAccent.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    UltraSettingRow(
                        icon = Icons.Default.VolumeUp,
                        title = NexaStrings.get("auto_speak", uiState.language)
                    ) {
                        Switch(
                            checked = uiState.autoSpeak,
                            onCheckedChange = { onToggleAutoSpeak() },
                            colors = SwitchDefaults.colors(checkedTrackColor = NexaAccent),
                            modifier = Modifier.scale(0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "CUENTA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexaAccent.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    UltraSettingRow(
                        icon = if (user.isLoggedIn) Icons.Default.Person else Icons.Default.PersonOutline,
                        title = if (user.isLoggedIn) user.email else NexaStrings.get("login", uiState.language),
                        value = if (user.isLoggedIn) NexaStrings.get("logout", uiState.language) else null,
                        onClick = {
                            if (user.isLoggedIn) onLogout() else onNavigateToLogin()
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        
        // Footer Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewMode = if (viewMode == 0) 1 else 0 }) {
                Icon(
                    if (viewMode == 0) Icons.Default.Settings else Icons.Default.History,
                    contentDescription = null,
                    tint = if (viewMode == 1) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "NEXA v2.2 ULTRA",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Version
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "NEXA PRO v2.1 • ${sessions.size} chats",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun DrawerSettingItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .width(56.dp)
    ) {
        Icon(
            icon, 
            contentDescription = label, 
            modifier = Modifier.size(18.dp), 
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label, 
            fontSize = 8.sp, 
            color = MaterialTheme.colorScheme.onSurfaceVariant, 
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
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
                    "${session.messages.size} ${NexaStrings.get("messages_count", language)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

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
                    Text("NEXA PRO", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 1.5.sp)
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
            IconButton(onClick = onToggleDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Menú", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        actions = {
            IconButton(onClick = onToggleAutoSpeak) {
                Icon(
                    if (uiState.autoSpeak) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = if (uiState.autoSpeak) "Desactivar voz" else "Activar voz",
                    tint = if (uiState.autoSpeak) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uiState.isSpeaking) {
                IconButton(onClick = onStopSpeaking) {
                    Icon(Icons.Default.StopCircle, contentDescription = "Detener", tint = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onClearChat) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Limpiar chat")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("❌ $error", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.weight(1f))
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
    language: AppLanguage,
    speakingMessageId: String?,
    onSpeakMessage: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
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
            item { EmptyState(language) }
        }

        items(messages, key = { it.id }) { msg ->
            MessageBubble(
                message = msg,
                isSpeaking = speakingMessageId == msg.id,
                onSpeak = { onSpeakMessage(msg.content, msg.id) }
            )
        }

        if (isThinking) {
            item { ThinkingIndicator(language) }
        }
    }
}

@Composable
fun EmptyState(lang: AppLanguage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🧬", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("NEXA PRO", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            NexaStrings.get("welcome_msg", lang),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speaker Button
                IconButton(onClick = onSpeak, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                        contentDescription = if (isSpeaking) "Detener" else "Leer",
                        modifier = Modifier.size(16.dp),
                        tint = if (isSpeaking) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Copy Button
                IconButton(onClick = { /* Copy logic would go here */ }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copiar",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Three Dots Menu
                var showMsgMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMsgMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = "Más opciones",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMsgMenu,
                        onDismissRequest = { showMsgMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.BugReport, null, modifier = Modifier.size(18.dp))
                                Text("Report Issue")
                            }},
                            onClick = { showMsgMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                                Text("Export to PDF")
                            }},
                            onClick = { showMsgMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(18.dp))
                                Text("Read aloud")
                            }},
                            onClick = { 
                                showMsgMenu = false
                                onSpeak()
                            }
                        )
                        DropdownMenuItem(
                            text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Forum, null, modifier = Modifier.size(18.dp))
                                Text("Start Thread")
                            }},
                            onClick = { showMsgMenu = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingIndicator(lang: AppLanguage) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(3) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NexaAccent))
        }
        Text(NexaStrings.get("thinking", lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DotsTyping() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NexaAccent))
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
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cross/X button with menu
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Menú",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("📎", fontSize = 18.sp)
                                    Text("Adjuntar archivo", fontSize = 14.sp)
                                }},
                                onClick = { showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🖼️", fontSize = 18.sp)
                                    Text("Enviar imagen", fontSize = 14.sp)
                                }},
                                onClick = { showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🧹", fontSize = 18.sp)
                                    Text(NexaStrings.get("clear_chat", language), fontSize = 14.sp)
                                }},
                                onClick = { showMenu = false }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                            DropdownMenuItem(
                                text = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("⚙️", fontSize = 18.sp)
                                    Text(NexaStrings.get("settings", language), fontSize = 14.sp)
                                }},
                                onClick = { showMenu = false }
                            )
                        }
                    }

                    // Text input
                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp),
                        placeholder = {
                            Text(
                                if (isListening) NexaStrings.get("listening", language)
                                else NexaStrings.get("input_hint", language),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
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
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp)
                    )

                    // Mic button
                    IconButton(
                        onClick = { if (isListening) onStopListening() else onStartListening() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                else NexaAccent.copy(alpha = 0.12f)
                            )
                    ) {
                        Icon(
                            if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isListening) "Detener" else "Hablar",
                            tint = if (isListening) MaterialTheme.colorScheme.error else NexaAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Stop TTS
                    if (isSpeaking) {
                        IconButton(
                            onClick = onStopSpeaking,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                Icons.Default.StopCircle,
                                contentDescription = "Detener lectura",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
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
                            .size(40.dp)
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
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Hint
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    NexaStrings.get("mic_hint", language),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
        }
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
    val lang = uiState.language
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("⚙️ ${NexaStrings.get("settings", lang)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 16.dp))

            // Row 1: Theme & Language (Compact)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactSettingCard(
                    modifier = Modifier.weight(1f),
                    title = NexaStrings.get("theme", lang),
                    icon = if (uiState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode
                ) {
                    Switch(
                        checked = uiState.isDarkTheme,
                        onCheckedChange = { onToggleTheme() },
                        colors = SwitchDefaults.colors(checkedTrackColor = NexaAccent),
                        modifier = Modifier.scale(0.8f)
                    )
                }
                CompactSettingCard(
                    modifier = Modifier.weight(1f),
                    title = NexaStrings.get("language", lang),
                    icon = Icons.Default.Language
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppLanguage.entries.forEach { l ->
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSetLanguage(l) },
                                color = if (uiState.language == l) NexaAccent.copy(alpha = 0.2f) else Color.Transparent,
                                border = if (uiState.language == l) BorderStroke(1.dp, NexaAccent) else null
                            ) {
                                Text(
                                    l.code.uppercase(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.language == l) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 2: Voice Selection (4 Voices Grid)
            Text(NexaStrings.get("voice", lang), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(VoiceType.entries.toList()) { voice ->
                    val isMale = voice == VoiceType.MALE_1 || voice == VoiceType.MALE_2
                    val labelKey = voice.name.lowercase()
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSetVoiceType(voice) },
                        color = if (uiState.voiceType == voice) NexaAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = if (uiState.voiceType == voice) BorderStroke(1.5.dp, NexaAccent) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(if (isMale) "👨" else "👩", fontSize = 16.sp)
                            Text(
                                NexaStrings.get(labelKey, lang),
                                fontSize = 11.sp,
                                fontWeight = if (uiState.voiceType == voice) FontWeight.Bold else FontWeight.Normal,
                                color = if (uiState.voiceType == voice) NexaAccent else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 3: Auto Speak
            CompactSettingCard(
                modifier = Modifier.fillMaxWidth(),
                title = NexaStrings.get("auto_speak", lang),
                subtitle = if (uiState.autoSpeak) NexaStrings.get("auto_speak_desc", lang) else NexaStrings.get("text_only", lang),
                icon = if (uiState.autoSpeak) Icons.Default.VolumeUp else Icons.Default.VolumeOff
            ) {
                Switch(
                    checked = uiState.autoSpeak,
                    onCheckedChange = { onToggleAutoSpeak() },
                    colors = SwitchDefaults.colors(checkedTrackColor = NexaAccent),
                    modifier = Modifier.scale(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun CompactSettingCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = NexaAccent)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
fun SettingRow(title: String, subtitle: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

// ═══════════════════════════════════════
//  UPDATE DIALOG
// ═══════════════════════════════════════

@Composable
fun UpdateDialog(
    updateInfo: com.nexa.ai.data.UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!updateInfo.forceUpdate) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🔄", fontSize = 24.sp)
                Text("Actualización disponible", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    "Nueva versión: ${updateInfo.versionName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NexaAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    updateInfo.changelog,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(containerColor = NexaAccent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Actualizar", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (!updateInfo.forceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text("Después", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}

// ═══════════════════════════════════════
//  TRANSLATIONS
// ═══════════════════════════════════════

object NexaStrings {
    fun get(key: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.SPANISH -> when (key) {
                "new_chat" -> "Nuevo chat"
                "settings" -> "Ajustes"
                "language" -> "Idioma"
                "voice" -> "Voz"
                "theme" -> "Tema"
                "dark" -> "Oscuro"
                "light" -> "Claro"
                "male_1" -> "Hombre 1"
                "male_2" -> "Hombre 2"
                "female_1" -> "Mujer 1"
                "female_2" -> "Mujer 2"
                "login" -> "Iniciar sesión"
                "logout" -> "Cerrar sesión"
                "register" -> "Registrarse"
                "email" -> "Email"
                "password" -> "Contraseña"
                "thinking" -> "pensando..."
                "input_hint" -> "Escribe un mensaje..."
                "listening" -> "🎙️ Escuchando..."
                "mic_hint" -> "🎙️ hablar • ↵ enviar"
                "messages_count" -> "mensajes"
                "delete_chat" -> "Borrar chat"
                "auto_speak" -> "Lectura automática"
                "auto_speak_desc" -> "NEXA habla las respuestas"
                "text_only" -> "Solo texto"
                "welcome_msg" -> "Toca el micrófono y habla,\no escribe tu mensaje."
                "clear_chat" -> "Limpiar chat"
                "attach" -> "Adjuntar archivo"
                "send_img" -> "Enviar imagen"
                "back" -> "Volver"
                "update_available" -> "Actualización disponible"
                "update_now" -> "Actualizar"
                "later" -> "Después"
                else -> key
            }
            AppLanguage.ENGLISH -> when (key) {
                "new_chat" -> "New Chat"
                "settings" -> "Settings"
                "language" -> "Language"
                "voice" -> "Voice"
                "theme" -> "Theme"
                "dark" -> "Dark"
                "light" -> "Light"
                "male_1" -> "Male 1"
                "male_2" -> "Male 2"
                "female_1" -> "Female 1"
                "female_2" -> "Female 2"
                "login" -> "Login"
                "logout" -> "Logout"
                "register" -> "Register"
                "email" -> "Email"
                "password" -> "Password"
                "thinking" -> "thinking..."
                "input_hint" -> "Type a message..."
                "listening" -> "🎙️ Listening..."
                "mic_hint" -> "🎙️ speak • ↵ send"
                "messages_count" -> "messages"
                "delete_chat" -> "Delete chat"
                "auto_speak" -> "Auto-speak"
                "auto_speak_desc" -> "NEXA speaks responses"
                "text_only" -> "Text only"
                "welcome_msg" -> "Tap the mic and speak,\nor type your message."
                "clear_chat" -> "Clear chat"
                "attach" -> "Attach file"
                "send_img" -> "Send image"
                "back" -> "Back"
                "update_available" -> "Update Available"
                "update_now" -> "Update"
                "later" -> "Later"
                else -> key
            }
        }
    }
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = NexaAccent)
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }

        if (value != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }

        content?.invoke()
    }
}

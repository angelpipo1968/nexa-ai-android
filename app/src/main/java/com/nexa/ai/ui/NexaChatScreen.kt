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
import androidx.compose.ui.platform.LocalTextStyle
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
                sessions = uiState.sessions,
                activeSessionId = uiState.activeSessionId,
                user = uiState.user,
                onNewChat = onCreateSession,
                onSwitchSession = onSwitchSession,
                onDeleteSession = onDeleteSession,
                onClose = { coroutineScope.launch { drawerState.close() } },
                onNavigateToLogin = onNavigateToLogin,
                onLogout = onLogout
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
                    speakingMessageId = uiState.speakingMessageId,
                    onSpeakMessage = onSpeakMessage,
                    modifier = Modifier.weight(1f)
                )

                BottomSettingsBar(
                    uiState = uiState,
                    onSetLanguage = onSetLanguage,
                    onSetVoiceType = onSetVoiceType,
                    onToggleTheme = onToggleTheme,
                    onToggleSettings = onToggleSettings
                )

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
    user: UserData,
    onNewChat: () -> Unit,
    onSwitchSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClose: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onLogout: () -> Unit
) {
    var showUserMenu by remember { mutableStateOf(false) }

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

                // User menu
                Box {
                    IconButton(onClick = { showUserMenu = true }) {
                        Icon(
                            if (user.isLoggedIn) Icons.Default.Person else Icons.Default.PersonOutline,
                            contentDescription = "Usuario",
                            tint = if (user.isLoggedIn) NexaAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showUserMenu,
                        onDismissRequest = { showUserMenu = false }
                    ) {
                        if (user.isLoggedIn) {
                            DropdownMenuItem(
                                text = { Text("📧 ${user.email}") },
                                onClick = { showUserMenu = false },
                                enabled = false
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("🚪 Cerrar sesión") },
                                onClick = {
                                    showUserMenu = false
                                    onLogout()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("🔑 Iniciar sesión") },
                                onClick = {
                                    showUserMenu = false
                                    onNavigateToLogin()
                                }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

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

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Version
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "NEXA PRO v2.1 • ${sessions.size} chats",
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
        Text("NEXA PRO", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
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
                IconButton(onClick = onSpeak, modifier = Modifier.size(28.dp)) {
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

@Composable
fun ThinkingIndicator() {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(3) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NexaAccent))
        }
        Text("pensando...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    Text("Limpiar chat", fontSize = 14.sp)
                                }},
                                onClick = { showMenu = false }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                            DropdownMenuItem(
                                text = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("⚙️", fontSize = 18.sp)
                                    Text("Ajustes", fontSize = 14.sp)
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
                                if (isListening) "🎙️ Escuchando..." else "Escribe un mensaje...",
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
                    "🎙️ hablar • ↵ enviar",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomSettingItem(Icons.Default.Settings, "Ajustes", onClick = onToggleSettings)

            BottomSettingItem(Icons.Default.Language, uiState.language.label, onClick = {
                val next = if (uiState.language == AppLanguage.SPANISH) AppLanguage.ENGLISH else AppLanguage.SPANISH
                onSetLanguage(next)
            })

            BottomSettingItem(
                if (uiState.voiceType == VoiceType.MALE) Icons.Default.Man else Icons.Default.Woman,
                if (uiState.voiceType == VoiceType.MALE) "Hombre" else "Mujer",
                onClick = {
                    val next = if (uiState.voiceType == VoiceType.MALE) VoiceType.FEMALE else VoiceType.MALE
                    onSetVoiceType(next)
                }
            )

            BottomSettingItem(
                if (uiState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                if (uiState.isDarkTheme) "Oscuro" else "Claro",
                onClick = onToggleTheme
            )
        }
    }
}

@Composable
fun BottomSettingItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
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
                .verticalScroll(rememberScrollState())
        ) {
            Text("⚙️ Ajustes", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 20.dp))

            SettingRow("Tema", if (uiState.isDarkTheme) "Oscuro" else "Claro") {
                Switch(
                    checked = uiState.isDarkTheme,
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(checkedTrackColor = NexaAccent)
                )
            }

            SettingRow("Idioma", uiState.language.label) {
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

            SettingRow("Voz", if (uiState.voiceType == VoiceType.MALE) "Hombre" else "Mujer") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VoiceType.entries.forEach { voice ->
                        FilterChip(
                            selected = uiState.voiceType == voice,
                            onClick = { onSetVoiceType(voice) },
                            label = { Text(if (voice == VoiceType.MALE) "👨 Hombre" else "👩 Mujer", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NexaAccent.copy(alpha = 0.15f),
                                selectedLabelColor = NexaAccent
                            )
                        )
                    }
                }
            }

            SettingRow("Lectura automática", if (uiState.autoSpeak) "NEXA habla las respuestas" else "Solo texto") {
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

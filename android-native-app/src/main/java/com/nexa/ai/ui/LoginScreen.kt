package com.nexa.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.viewmodel.AppLanguage

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
        color = if (isDarkTheme) Color(0xFF050508) else Color(0xFFF8F9FC)
    ) {
        // Center content on wide screens
        CenteredContent(maxWidth = AdaptiveDimens.maxAuthContentWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AdaptiveDimens.horizontalPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Back button
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = NexaStrings.get("back", language),
                            tint = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f))
                    }
                }

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingXl()))

                // Logo
                AuthLogo()

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingLg()))
                Text("NEXA PRO", fontSize = AdaptiveTypography.displayLarge(), fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Text(NexaStrings.get("login_title", language), fontSize = AdaptiveTypography.bodySmall(),
                    color = if (isDarkTheme) Color(0xFF6B6B80) else Color(0xFF5A5A70),
                    modifier = Modifier.padding(top = 6.dp), letterSpacing = 0.5.sp)

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingXl()))

                // Email field
                AuthTextField(value = email, onValueChange = onEmailChange,
                    label = NexaStrings.get("email", language), placeholder = NexaStrings.get("email_placeholder", language),
                    leadingIcon = Icons.Default.Email, isDarkTheme = isDarkTheme,
                    keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingMd()))

                // Password field
                AuthPasswordField(value = password, onValueChange = onPasswordChange,
                    label = NexaStrings.get("password", language), showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword }, isDarkTheme = isDarkTheme,
                    imeAction = ImeAction.Done,
                    onDone = { onLogin(); keyboardController?.hide() })

                // Error
                if (error != null) {
                    Spacer(modifier = Modifier.height(AdaptiveDimens.spacingSm()))
                    AuthErrorBanner(error)
                }

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingLg()))

                // Login button
                AuthButton(text = NexaStrings.get("login", language), isLoading = isLoading,
                    onClick = { onLogin(); keyboardController?.hide() })

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingLg()))

                // Divider
                AuthDivider(isDarkTheme = isDarkTheme)

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingLg()))

                // Register link
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(NexaStrings.get("no_account", language) + " ", fontSize = AdaptiveTypography.bodyMedium(),
                        color = if (isDarkTheme) Color(0xFF888888) else Color(0xFF666666))
                    Text(NexaStrings.get("register", language), fontSize = AdaptiveTypography.bodyMedium(), fontWeight = FontWeight.Bold,
                        color = NexaAccent, modifier = Modifier.clickable { onGoToRegister() })
                }

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingXxl()))
            }
        }
    }
}

// ═══════════════════════════════════════
//  REGISTER SCREEN
// ═══════════════════════════════════════

@Composable
fun RegisterScreen(
    name: String, email: String, password: String, confirmPassword: String,
    error: String?, isLoading: Boolean,
    onNameChange: (String) -> Unit, onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit, onConfirmPasswordChange: (String) -> Unit,
    onRegister: () -> Unit, onGoToLogin: () -> Unit, onBack: () -> Unit,
    isDarkTheme: Boolean, language: AppLanguage
) {
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDarkTheme) Color(0xFF050508) else Color(0xFFF8F9FC)
    ) {
        CenteredContent(maxWidth = AdaptiveDimens.maxAuthContentWidth()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = AdaptiveDimens.horizontalPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.Start) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = NexaStrings.get("back", language),
                            tint = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f))
                    }
                }

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingLg()))
                AuthLogo()
                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingLg()))
                Text("NEXA PRO", fontSize = AdaptiveTypography.displayLarge(), fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Text(NexaStrings.get("create_account", language), fontSize = AdaptiveTypography.bodySmall(),
                    color = if (isDarkTheme) Color(0xFF6B6B80) else Color(0xFF5A5A70),
                    modifier = Modifier.padding(top = 6.dp), letterSpacing = 0.5.sp)

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingXl()))

                AuthTextField(value = name, onValueChange = onNameChange,
                    label = NexaStrings.get("name", language), placeholder = NexaStrings.get("your_name", language),
                    leadingIcon = Icons.Default.Person, isDarkTheme = isDarkTheme, imeAction = ImeAction.Next)

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingSm()))

                AuthTextField(value = email, onValueChange = onEmailChange,
                    label = NexaStrings.get("email", language), placeholder = NexaStrings.get("email_placeholder", language),
                    leadingIcon = Icons.Default.Email, isDarkTheme = isDarkTheme,
                    keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingSm()))

                AuthPasswordField(value = password, onValueChange = onPasswordChange,
                    label = NexaStrings.get("password", language), showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword }, isDarkTheme = isDarkTheme,
                    placeholder = NexaStrings.get("min_6", language), imeAction = ImeAction.Next)

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingSm()))

                AuthPasswordField(value = confirmPassword, onValueChange = onConfirmPasswordChange,
                    label = NexaStrings.get("confirm_password", language), showPassword = showConfirm,
                    onTogglePassword = { showConfirm = !showConfirm }, isDarkTheme = isDarkTheme,
                    placeholder = NexaStrings.get("repeat_password", language), imeAction = ImeAction.Done,
                    onDone = { onRegister(); keyboardController?.hide() })

                if (error != null) {
                    Spacer(modifier = Modifier.height(AdaptiveDimens.spacingSm()))
                    AuthErrorBanner(error)
                }

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingLg()))

                AuthButton(text = NexaStrings.get("create_account_btn", language), isLoading = isLoading,
                    onClick = { onRegister(); keyboardController?.hide() })

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingLg()))

                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(NexaStrings.get("has_account", language) + " ", fontSize = AdaptiveTypography.bodyMedium(),
                        color = if (isDarkTheme) Color(0xFF888888) else Color(0xFF666666))
                    Text(NexaStrings.get("login", language), fontSize = AdaptiveTypography.bodyMedium(), fontWeight = FontWeight.Bold,
                        color = NexaAccent, modifier = Modifier.clickable { onGoToLogin() })
                }

                Spacer(modifier = Modifier.height(AdaptiveDimens.spacingXxl()))
            }
        }
    }
}

// ═══════════════════════════════════════
//  SHARED AUTH COMPONENTS
// ═══════════════════════════════════════

@Composable
private fun AuthLogo() {
    val logoSize = AdaptiveDimens.logoSize()
    Box(
        modifier = Modifier.size(logoSize).clip(RoundedCornerShape(AdaptiveDimens.cornerLarge()))
            .background(Brush.radialGradient(listOf(NexaAccent.copy(alpha = 0.12f), NexaAccent.copy(alpha = 0.02f)))),
        contentAlignment = Alignment.Center
    ) { Text("⚡", fontSize = AdaptiveTypography.displayLarge()) }
}

@Composable
private fun AuthTextField(
    value: String, onValueChange: (String) -> Unit, label: String, placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector, isDarkTheme: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text, imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        placeholder = { Text(placeholder) }, leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(AdaptiveDimens.cornerMedium()),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NexaAccent, focusedLabelColor = NexaAccent, cursorColor = NexaAccent))
}

@Composable
private fun AuthPasswordField(
    value: String, onValueChange: (String) -> Unit, label: String, showPassword: Boolean,
    onTogglePassword: () -> Unit, isDarkTheme: Boolean, placeholder: String = "••••••••",
    imeAction: ImeAction = ImeAction.Done, onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onTogglePassword) {
                Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
            }
        }, singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(AdaptiveDimens.cornerMedium()),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NexaAccent, focusedLabelColor = NexaAccent, cursorColor = NexaAccent))
}

@Composable
private fun AuthErrorBanner(error: String) {
    Surface(shape = RoundedCornerShape(AdaptiveDimens.cornerMedium()), color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(AdaptiveDimens.spacingMd()), horizontalArrangement = Arrangement.spacedBy(AdaptiveDimens.spacingSm()),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(AdaptiveDimens.iconMedium()), tint = MaterialTheme.colorScheme.error)
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = AdaptiveTypography.bodySmall())
        }
    }
}

@Composable
private fun AuthButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(AdaptiveDimens.buttonHeight()),
        shape = RoundedCornerShape(AdaptiveDimens.cornerMedium()), colors = ButtonDefaults.buttonColors(containerColor = NexaAccent),
        enabled = !isLoading) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
        } else {
            Text(text, fontSize = AdaptiveTypography.labelLarge(), fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
private fun AuthDivider(isDarkTheme: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFDDDDDD))
        Text("  o  ", fontSize = AdaptiveTypography.bodySmall(), color = if (isDarkTheme) Color(0xFF666666) else Color(0xFF999999))
        HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFDDDDDD))
    }
}

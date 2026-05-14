package com.nexa.ai.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.ui.theme.NexaAccentDark
import com.nexa.ai.ui.theme.NexaAccentLight
import com.nexa.ai.viewmodel.*

// ═══════════════════════════════════════
//  SETTINGS SCREEN (full page — polished)
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: NexaUiState,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetVoiceType: (VoiceType) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onClearChat: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                            .background(Brush.radialGradient(listOf(NexaAccent.copy(alpha = 0.18f), NexaAccent.copy(alpha = 0.04f)))),
                            contentAlignment = Alignment.Center) { Text("⚡", fontSize = 15.sp) }
                        Column {
                            Text(NexaStrings.get("settings", uiState.language), fontWeight = FontWeight.Black,
                                fontSize = 17.sp, letterSpacing = 1.5.sp)
                            Text("NEXA PRO", fontSize = 8.sp, color = NexaAccent.copy(alpha = 0.45f),
                                letterSpacing = 2.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            // ════════════════════════════════
            //  LANGUAGE
            // ════════════════════════════════
            SettingsCard {
                SettingsHeader(icon = Icons.Default.Language, title = NexaStrings.get("language", uiState.language))
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(AppLanguage.SPANISH to "Español 🇪🇸", AppLanguage.ENGLISH to "English 🇺🇸").forEach { (lang, label) ->
                        val selected = uiState.language == lang
                        Surface(
                            modifier = Modifier.weight(1f).clickable { onSetLanguage(lang) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) NexaAccent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            border = if (selected) BorderStroke(1.5.dp, NexaAccent.copy(alpha = 0.35f))
                            else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (selected) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NexaAccent))
                                }
                                Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) NexaAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    letterSpacing = 0.3.sp)
                            }
                        }
                    }
                }
            }

            // ════════════════════════════════
            //  VOICE
            // ════════════════════════════════
            SettingsCard {
                SettingsHeader(icon = Icons.Default.RecordVoiceOver, title = NexaStrings.get("voice", uiState.language))
                Spacer(modifier = Modifier.height(14.dp))
                val voices = VoiceType.entries.toList()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    voices.chunked(3).forEach { rowVoices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            rowVoices.forEach { voice ->
                                val isMale = voice.name.contains("MALE")
                                val selected = uiState.voiceType == voice
                                Surface(
                                    modifier = Modifier.weight(1f).clickable { onSetVoiceType(voice) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    border = if (selected) BorderStroke(1.5.dp, NexaAccent.copy(alpha = 0.45f))
                                    else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                                ) {
                                    Box(
                                        modifier = if (selected) Modifier.background(
                                            Brush.verticalGradient(listOf(NexaAccent.copy(alpha = 0.10f), NexaAccent.copy(alpha = 0.03f)))
                                        ) else Modifier
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(36.dp).clip(CircleShape)
                                                    .background(if (selected) NexaAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(if (isMale) "👨" else "👩", fontSize = 18.sp)
                                            }
                                            Text(
                                                NexaStrings.get(voice.name.lowercase(), uiState.language),
                                                fontSize = 10.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) NexaAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                                textAlign = TextAlign.Center,
                                                letterSpacing = 0.3.sp
                                            )
                                            if (selected) {
                                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(NexaAccent))
                                            } else {
                                                Spacer(modifier = Modifier.height(5.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            // Fill remaining slots if not divisible by 3
                            repeat(3 - rowVoices.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // ════════════════════════════════
            //  THEME
            // ════════════════════════════════
            SettingsCard {
                SettingsHeader(icon = Icons.Default.Palette, title = NexaStrings.get("theme", uiState.language))
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeOption(
                        mode = ThemeMode.DARK,
                        label = NexaStrings.get("dark", uiState.language),
                        emoji = "🌙",
                        previewTop = Color(0xFF1A1A24),
                        previewBottom = Color(0xFF0D0D12),
                        previewAccent = NexaAccent,
                        selected = uiState.themeMode == ThemeMode.DARK,
                        onClick = { onSetThemeMode(ThemeMode.DARK) }
                    )
                    ThemeOption(
                        mode = ThemeMode.LIGHT,
                        label = NexaStrings.get("light", uiState.language),
                        emoji = "☀️",
                        previewTop = Color(0xFFF8F9FC),
                        previewBottom = Color(0xFFFFFFFF),
                        previewAccent = NexaAccentDark,
                        selected = uiState.themeMode == ThemeMode.LIGHT,
                        onClick = { onSetThemeMode(ThemeMode.LIGHT) }
                    )
                    ThemeOption(
                        mode = ThemeMode.SYSTEM,
                        label = NexaStrings.get("system", uiState.language),
                        emoji = "⚙️",
                        previewTop = Color(0xFF1A1A24),
                        previewBottom = Color(0xFFF8F9FC),
                        previewAccent = NexaAccent,
                        selected = uiState.themeMode == ThemeMode.SYSTEM,
                        onClick = { onSetThemeMode(ThemeMode.SYSTEM) },
                        isSystem = true
                    )
                }
            }

            // ════════════════════════════════
            //  AUTO-SPEAK
            // ════════════════════════════════
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                            .background(NexaAccent.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(16.dp), tint = NexaAccent.copy(alpha = 0.7f))
                        }
                        Column {
                            Text(NexaStrings.get("auto_speak", uiState.language), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(NexaStrings.get("auto_speak_desc", uiState.language), fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), lineHeight = 15.sp)
                        }
                    }
                    Switch(
                        checked = uiState.autoSpeak,
                        onCheckedChange = { onToggleAutoSpeak() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = NexaAccent.copy(alpha = 0.40f),
                            checkedThumbColor = NexaAccent,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    )
                }
            }

            // ════════════════════════════════
            //  DANGER ZONE
            // ════════════════════════════════
            SettingsCard {
                Text("⚠️ ${if (uiState.language == AppLanguage.SPANISH) "Zona de peligro" else "Danger zone"}",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // Clear chat
                Surface(
                    onClick = onClearChat,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.05f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.10f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Delete, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.65f), modifier = Modifier.size(15.dp))
                        }
                        Text(NexaStrings.get("clear_chat", uiState.language),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), fontSize = 13.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Login / Logout
                if (uiState.user.isLoggedIn) {
                    Surface(
                        onClick = onLogout,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.05f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.10f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.65f), modifier = Modifier.size(15.dp))
                            }
                            Column {
                                Text(NexaStrings.get("logout", uiState.language),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium)
                                Text(uiState.user.email, fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                            }
                        }
                    }
                } else {
                    Surface(
                        onClick = onNavigateToLogin,
                        shape = RoundedCornerShape(14.dp),
                        color = NexaAccent.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, NexaAccent.copy(alpha = 0.20f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                                .background(NexaAccent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null,
                                    tint = NexaAccent, modifier = Modifier.size(16.dp))
                            }
                            Text(NexaStrings.get("login", uiState.language),
                                color = NexaAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ═══════════════════════════════════════
//  THEME OPTION CARD
// ═══════════════════════════════════════

@Composable
private fun RowScope.ThemeOption(
    mode: ThemeMode,
    label: String,
    emoji: String,
    previewTop: Color,
    previewBottom: Color,
    previewAccent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    isSystem: Boolean = false
) {
    Surface(
        modifier = Modifier.weight(1f).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) NexaAccent.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        border = if (selected) BorderStroke(1.5.dp, NexaAccent.copy(alpha = 0.40f))
        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mini phone preview
            Box(
                modifier = Modifier
                    .size(44.dp, 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.verticalGradient(listOf(previewTop, previewBottom)))
                    .drawBehind {
                        // Accent bar at top
                        drawRoundRect(
                            color = previewAccent.copy(alpha = 0.5f),
                            topLeft = Offset(4.dp.toPx(), 3.dp.toPx()),
                            size = Size(size.width - 8.dp.toPx(), 2.dp.toPx()),
                            cornerRadius = CornerRadius(1.dp.toPx())
                        )
                        // Fake content lines
                        drawRoundRect(
                            color = previewAccent.copy(alpha = 0.15f),
                            topLeft = Offset(4.dp.toPx(), 8.dp.toPx()),
                            size = Size(size.width * 0.6f, 1.5.dp.toPx()),
                            cornerRadius = CornerRadius(0.5.dp.toPx())
                        )
                        drawRoundRect(
                            color = previewAccent.copy(alpha = 0.10f),
                            topLeft = Offset(4.dp.toPx(), 11.5.dp.toPx()),
                            size = Size(size.width * 0.45f, 1.5.dp.toPx()),
                            cornerRadius = CornerRadius(0.5.dp.toPx())
                        )
                        // Bottom bar
                        drawRoundRect(
                            color = previewAccent.copy(alpha = 0.20f),
                            topLeft = Offset(size.width * 0.3f, size.height - 5.dp.toPx()),
                            size = Size(size.width * 0.4f, 1.5.dp.toPx()),
                            cornerRadius = CornerRadius(0.75.dp.toPx())
                        )
                    }
            ) {
                if (isSystem) {
                    // Split view: dark left, light right
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(
                            Brush.horizontalGradient(listOf(Color(0xFF1A1A24), Color(0xFF1A1A24).copy(alpha = 0.7f)))
                        ))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(
                            Brush.horizontalGradient(listOf(Color(0xFFF8F9FC).copy(alpha = 0.7f), Color(0xFFF8F9FC)))
                        ))
                    }
                }
            }

            Text(
                "$emoji $label",
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) NexaAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                textAlign = TextAlign.Center,
                letterSpacing = 0.3.sp
            )

            if (selected) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(NexaAccent))
            } else {
                Spacer(modifier = Modifier.height(5.dp))
            }
        }
    }
}

// ═══════════════════════════════════════
//  REUSABLE HELPERS
// ═══════════════════════════════════════

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsHeader(
    icon: ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
            .background(NexaAccent.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = NexaAccent.copy(alpha = 0.70f))
        }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            letterSpacing = 0.8.sp)
    }
}

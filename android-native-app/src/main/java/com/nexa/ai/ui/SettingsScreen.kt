package com.nexa.ai.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.BuildConfig
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.ui.theme.dynamicPrimaryColor
import com.nexa.ai.viewmodel.*

/** CompositionLocal providing the effective accent color for the current theme. */
val LocalAccentColor = compositionLocalOf { NexaAccent }

// ═══════════════════════════════════════════════════════════════
//  SETTINGS SCREEN — Clean Minimalist Redesign
// ═══════════════════════════════════════════════════════════════

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
    onLogout: () -> Unit,
    onRequestLocation: () -> Unit = {},
    onToggleNotifications: () -> Unit = {},
    onToggleVolumeBoost: () -> Unit = {},
    onSetSpeechRate: (Float) -> Unit = {},
    onQuickAction: (String) -> Unit = {},
    onPreviewVoice: () -> Unit = {},
    onSetAccentColor: (Color) -> Unit = {},
    onExportSettings: () -> Unit = {},
    onImportSettings: () -> Unit = {}
) {
    // Standardized spacing measurement for uniformity — using adaptive system
    val sectionSpacing = AdaptiveDimens.sectionSpacing()
    val internalSpacing = AdaptiveDimens.spacingMd()

    val effectiveAccent = if (uiState.accentColor != 0L) Color(uiState.accentColor) else if (uiState.themeMode == ThemeMode.SYSTEM) dynamicPrimaryColor() else NexaAccent

    CompositionLocalProvider(LocalAccentColor provides effectiveAccent) {

    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(4000, easing = EaseInOut), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.size(300.dp).offset((-50).dp, (-100).dp).blur(120.dp).background(effectiveAccent.copy(alpha = glowAlpha)))
        Box(modifier = Modifier.size(250.dp).offset(200.dp, 500.dp).blur(120.dp).background(Color(0xFF0066FF).copy(alpha = glowAlpha * 0.5f)))

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            NexaStrings.get("settings", uiState.language).uppercase(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        MinimalIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(18.dp))
                        }
                    },
                    actions = {
                        MinimalIconButton(onClick = { /* Info */ }) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            var sectionsVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { sectionsVisible = true }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AdaptiveDimens.horizontalPadding(), vertical = AdaptiveDimens.verticalPadding()),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {

                // ── Brand ──
                StaggeredFadeIn(visible = sectionsVisible, index = 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(effectiveAccent, effectiveAccent.copy(alpha = 0.7f)))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                        }
                        Text("NEXA", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = effectiveAccent)
                        Surface(shape = RoundedCornerShape(6.dp), color = effectiveAccent.copy(alpha = 0.12f)) {
                            Text("PRO", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = effectiveAccent)
                        }
                    }
                }

                // ── Language ──
                StaggeredFadeIn(visible = sectionsVisible, index = 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(internalSpacing)) {
                        SectionLabel(NexaStrings.get("language", uiState.language).uppercase())
                        FuturisticCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(AppLanguage.SPANISH to "🇪🇸  Español", AppLanguage.ENGLISH to "🇺🇸  English").forEach { (lang, label) ->
                                    val selected = uiState.language == lang
                                    FuturisticPill(label = label, selected = selected, accent = effectiveAccent, modifier = Modifier.weight(1f), onClick = { onSetLanguage(lang) })
                                }
                            }
                        }
                    }
                }

                // ── Voice ──
                StaggeredFadeIn(visible = sectionsVisible, index = 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(internalSpacing)) {
                        SectionLabel(NexaStrings.get("voice", uiState.language).uppercase())
                        FuturisticCard {
                            // Sub-label for consistency
                            Text(NexaStrings.get("male_label", uiState.language).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = effectiveAccent.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                VoiceType.entries.filter { it.name.startsWith("MALE") }.forEach { voice ->
                                    val selected = uiState.voiceType == voice
                                    VoiceCard(voice = voice, label = NexaStrings.get(voice.name.lowercase(), uiState.language), selected = selected, modifier = Modifier.weight(1f), onClick = { onSetVoiceType(voice) })
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(NexaStrings.get("female_label", uiState.language).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = effectiveAccent.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                VoiceType.entries.filter { it.name.contains("FEMALE") }.forEach { voice ->
                                    val selected = uiState.voiceType == voice
                                    VoiceCard(voice = voice, label = NexaStrings.get(voice.name.lowercase(), uiState.language), selected = selected, modifier = Modifier.weight(1f), onClick = { onSetVoiceType(voice) })
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            // Voice preview button
                            Surface(
                                onClick = { onPreviewVoice() },
                                shape = RoundedCornerShape(12.dp),
                                color = effectiveAccent.copy(alpha = 0.08f),
                                border = BorderStroke(0.5.dp, effectiveAccent.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp), tint = effectiveAccent)
                                    Text(
                                        NexaStrings.get("preview_voice", uiState.language),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = effectiveAccent
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Theme ──
                StaggeredFadeIn(visible = sectionsVisible, index = 3) {
                    Column(verticalArrangement = Arrangement.spacedBy(internalSpacing)) {
                        SectionLabel(NexaStrings.get("theme", uiState.language).uppercase())
                        FuturisticCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                ThemeOption(label = NexaStrings.get("dark", uiState.language), emoji = "🌙", previewTop = Color(0xFF1A1A24), previewBottom = Color(0xFF0D0D12), selected = uiState.themeMode == ThemeMode.DARK, onClick = { onSetThemeMode(ThemeMode.DARK) }, modifier = Modifier.weight(1f))
                                ThemeOption(label = NexaStrings.get("light", uiState.language), emoji = "☀️", previewTop = Color(0xFFF8F9FC), previewBottom = Color(0xFFFFFFFF), selected = uiState.themeMode == ThemeMode.LIGHT, onClick = { onSetThemeMode(ThemeMode.LIGHT) }, modifier = Modifier.weight(1f))
                                ThemeOption(label = NexaStrings.get("system", uiState.language), emoji = "⚙️", previewTop = Color(0xFF1A1A24), previewBottom = Color(0xFFF8F9FC), selected = uiState.themeMode == ThemeMode.SYSTEM, onClick = { onSetThemeMode(ThemeMode.SYSTEM) }, isSystem = true, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // ── Accent Color ──
                StaggeredFadeIn(visible = sectionsVisible, index = 4) {
                    Column(verticalArrangement = Arrangement.spacedBy(internalSpacing)) {
                        SectionLabel(NexaStrings.get("accent_color", uiState.language).uppercase())
                        FuturisticCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                listOf(
                                    Color(0xFF00F5A0) to "Emerald",
                                    Color(0xFF00B4D8) to "Ocean",
                                    Color(0xFF7C6AFF) to "Violet",
                                    Color(0xFFFF6B6B) to "Coral",
                                    Color(0xFFFFB800) to "Amber",
                                    Color(0xFFFF00E5) to "Magenta",
                                    Color(0xFF00E5FF) to "Cyan",
                                    Color(0xFF8B5CF6) to "Purple"
                                ).forEach { (color, name) ->
                                    val selected = effectiveAccent == color
                                    Surface(
                                        onClick = { onSetAccentColor(color) },
                                        shape = CircleShape,
                                        color = color.copy(alpha = if (selected) 1f else 0.5f),
                                        border = if (selected) BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)) else null,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        if (selected) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = Color.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Preferences ──
                StaggeredFadeIn(visible = sectionsVisible, index = 5) {
                    Column(verticalArrangement = Arrangement.spacedBy(internalSpacing)) {
                        SectionLabel(NexaStrings.get("preferences", uiState.language).uppercase())
                        FuturisticCard {
                            // Auto-speak
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(NexaStrings.get("auto_speak", uiState.language), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(NexaStrings.get("auto_speak_desc", uiState.language), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), lineHeight = 14.sp)
                                }
                                FuturisticSwitch(checked = uiState.autoSpeak, onCheckedChange = { onToggleAutoSpeak() })
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            // Volume Boost
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(effectiveAccent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, modifier = Modifier.size(16.dp), tint = effectiveAccent)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(NexaStrings.get("volume_boost", uiState.language), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(NexaStrings.get("volume_boost_desc", uiState.language), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), lineHeight = 14.sp)
                                }
                                FuturisticSwitch(checked = uiState.volumeBoostEnabled, onCheckedChange = { onToggleVolumeBoost() })
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            // Notifications
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(NexaStrings.get("notifications", uiState.language), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(NexaStrings.get("notifications_desc", uiState.language), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), lineHeight = 14.sp)
                                }
                                FuturisticSwitch(checked = uiState.notificationsEnabled, onCheckedChange = { onToggleNotifications() })
                            }
                        }
                    }
                }

                // ── Speech Rate ──
                StaggeredFadeIn(visible = sectionsVisible, index = 6) {
                    Column(verticalArrangement = Arrangement.spacedBy(internalSpacing)) {
                        SectionLabel(NexaStrings.get("speech_rate", uiState.language).uppercase())
                        FuturisticCard {
                            Column {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf(
                                        0.7f to NexaStrings.get("speech_rate_slow", uiState.language),
                                        1.0f to NexaStrings.get("speech_rate_normal", uiState.language),
                                        1.3f to NexaStrings.get("speech_rate_fast", uiState.language)
                                    ).forEach { (rate, label) ->
                                        val selected = kotlin.math.abs(uiState.speechRate - rate) < 0.05f
                                        FuturisticPill(label = label, selected = selected, accent = effectiveAccent, modifier = Modifier.weight(1f), onClick = { onSetSpeechRate(rate) })
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Location ──
                StaggeredFadeIn(visible = sectionsVisible, index = 7) {
                    Column(verticalArrangement = Arrangement.spacedBy(internalSpacing)) {
                        SectionLabel(NexaStrings.get("location", uiState.language).uppercase())
                        FuturisticCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(effectiveAccent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(16.dp), tint = effectiveAccent)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(NexaStrings.get("location", uiState.language), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    if (uiState.isLocating) {
                                        Text(NexaStrings.get("location_loading", uiState.language), fontSize = 11.sp, color = effectiveAccent.copy(alpha = 0.7f), lineHeight = 14.sp)
                                    } else if (uiState.locationData.isAvailable) {
                                        Text(uiState.locationData.city + ", " + uiState.locationData.country, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("%.4f, %.4f".format(uiState.locationData.latitude, uiState.locationData.longitude), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    } else {
                                        Text(NexaStrings.get("location_not_available", uiState.language), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), lineHeight = 14.sp)
                                    }
                                }
                                Surface(onClick = onRequestLocation, shape = RoundedCornerShape(10.dp), color = effectiveAccent.copy(alpha = 0.10f), border = BorderStroke(1.dp, effectiveAccent.copy(alpha = 0.25f))) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.GpsFixed, null, modifier = Modifier.size(12.dp), tint = effectiveAccent)
                                        Text(NexaStrings.get("location_request", uiState.language), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = effectiveAccent)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── AI Capabilities ──
                StaggeredFadeIn(visible = sectionsVisible, index = 8) {
                    Column(verticalArrangement = Arrangement.spacedBy(internalSpacing)) {
                        SectionLabel(NexaStrings.get("ai_capabilities", uiState.language).uppercase())
                        FuturisticCard {
                            // Quick actions info
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(effectiveAccent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = effectiveAccent)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(NexaStrings.get("ai_capabilities", uiState.language), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(NexaStrings.get("ai_capabilities_desc", uiState.language), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), lineHeight = 14.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            // Capability cards - 2x2 grid
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf(
                                        Triple(Icons.Default.Image, NexaStrings.get("create_image", uiState.language), "image"),
                                        Triple(Icons.Default.Language, NexaStrings.get("create_web", uiState.language), "web")
                                    ).forEach { (icon, label, action) ->
                                        Surface(
                                            onClick = { onQuickAction(action) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = effectiveAccent.copy(alpha = 0.06f),
                                            border = BorderStroke(0.5.dp, effectiveAccent.copy(alpha = 0.15f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(icon, null, modifier = Modifier.size(16.dp), tint = effectiveAccent.copy(alpha = 0.6f))
                                                Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = effectiveAccent.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf(
                                        Triple(Icons.Default.Brush, NexaStrings.get("create_logo", uiState.language), "logo"),
                                        Triple(Icons.Default.Code, NexaStrings.get("write_code", uiState.language), "code")
                                    ).forEach { (icon, label, action) ->
                                        Surface(
                                            onClick = { onQuickAction(action) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = effectiveAccent.copy(alpha = 0.06f),
                                            border = BorderStroke(0.5.dp, effectiveAccent.copy(alpha = 0.15f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(icon, null, modifier = Modifier.size(16.dp), tint = effectiveAccent.copy(alpha = 0.6f))
                                                Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = effectiveAccent.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Screen Adaptation ──
                StaggeredFadeIn(visible = sectionsVisible, index = 9) {
                    Column(verticalArrangement = Arrangement.spacedBy(internalSpacing)) {
                        SectionLabel(NexaStrings.get("screen_adaptation", uiState.language).uppercase())
                        FuturisticCard {
                            // Auto-scroll
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(NexaStrings.get("auto_scroll", uiState.language), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(NexaStrings.get("auto_scroll_desc", uiState.language), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), lineHeight = 14.sp)
                                }
                                FuturisticSwitch(checked = uiState.autoScrollEnabled, onCheckedChange = { /* Handled by ViewModel */ })
                            }
                        }
                    }
                }

                // ── Backup ──
                StaggeredFadeIn(visible = sectionsVisible, index = 10) {
                    Column(verticalArrangement = Arrangement.spacedBy(internalSpacing)) {
                        SectionLabel(NexaStrings.get("backup", uiState.language).uppercase())
                        FuturisticCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    onClick = onExportSettings,
                                    shape = RoundedCornerShape(12.dp),
                                    color = effectiveAccent.copy(alpha = 0.08f),
                                    border = BorderStroke(0.5.dp, effectiveAccent.copy(alpha = 0.2f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(14.dp), tint = effectiveAccent)
                                        Text(NexaStrings.get("export_settings", uiState.language), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = effectiveAccent)
                                    }
                                }
                                Surface(
                                    onClick = onImportSettings,
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                        Text(NexaStrings.get("import_settings", uiState.language), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Privacy ──
                StaggeredFadeIn(visible = sectionsVisible, index = 11) {
                    Column(verticalArrangement = Arrangement.spacedBy(internalSpacing)) {
                        SectionLabel(NexaStrings.get("privacy", uiState.language).uppercase())
                        FuturisticCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Shield, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(NexaStrings.get("privacy_cleartext", uiState.language), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(NexaStrings.get("privacy_cleartext_on", uiState.language), fontSize = 11.sp, color = Color(0xFF00C896), lineHeight = 14.sp)
                                }
                                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF00C896).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = Color(0xFF00C896))
                                }
                            }
                        }
                    }
                }

                // ── Danger Zone ──
                StaggeredFadeIn(visible = sectionsVisible, index = 12) {
                    Column(verticalArrangement = Arrangement.spacedBy(internalSpacing)) {
                        SectionLabel(NexaStrings.get("danger_zone", uiState.language).uppercase(), color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        FuturisticCard {
                            DangerButton(icon = Icons.Default.Delete, label = NexaStrings.get("clear_chat", uiState.language), onClick = onClearChat)
                            Spacer(modifier = Modifier.height(12.dp))
                            if (uiState.user.isLoggedIn) {
                                DangerButton(icon = Icons.AutoMirrored.Filled.ExitToApp, label = NexaStrings.get("logout", uiState.language), subtitle = uiState.user.email, onClick = onLogout)
                            } else {
                                Surface(onClick = onNavigateToLogin, shape = RoundedCornerShape(14.dp), color = effectiveAccent.copy(alpha = 0.08f), border = BorderStroke(1.dp, effectiveAccent.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(effectiveAccent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = effectiveAccent)
                                        }
                                        Text(NexaStrings.get("login", uiState.language), color = effectiveAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── About ──
                StaggeredFadeIn(visible = sectionsVisible, index = 13) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.width(40.dp).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), Color.Transparent))))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(NexaStrings.get("about_version", uiState.language), fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    }
}

// ═══════════════════════════════════════════════════════════════
//  STANDARD COMPONENTS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun StaggeredFadeIn(visible: Boolean, index: Int, durationMs: Int = 450, staggerMs: Int = 80, content: @Composable () -> Unit) {
    val animVisibleState = remember { MutableTransitionState(false) }
    animVisibleState.targetState = visible
    AnimatedVisibility(visibleState = animVisibleState, enter = fadeIn(tween(durationMs, delayMillis = index * staggerMs)) + slideInVertically(tween(durationMs, delayMillis = index * staggerMs), initialOffsetY = { it / 12 }), exit = fadeOut()) { content() }
}

@Composable
private fun SectionLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = color, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun FuturisticCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f), border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun FuturisticPill(label: String, selected: Boolean, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    Surface(modifier = modifier.scale(scale).pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }) }, shape = RoundedCornerShape(12.dp), color = if (selected) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), border = if (selected) BorderStroke(1.5.dp, accent.copy(alpha = 0.40f)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))) {
        Box(modifier = Modifier.fillMaxWidth().drawBehind { if (selected) drawRoundRect(Brush.linearGradient(listOf(accent.copy(alpha = 0.08f), Color.Transparent)), cornerRadius = CornerRadius(12.dp.toPx()), size = size) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f))
        }
    }
}

@Composable
private fun VoiceCard(voice: VoiceType, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccentColor.current
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    val infiniteTransition = rememberInfiniteTransition()
    val wavePhase by infiniteTransition.animateFloat(0f, 2f * Math.PI.toFloat(), infiniteRepeatable(tween(1200, easing = LinearEasing)))
    Surface(modifier = modifier.scale(scale).pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }) }, shape = RoundedCornerShape(14.dp), color = if (selected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), border = if (selected) BorderStroke(1.5.dp, accent.copy(alpha = 0.40f)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(if (selected) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            if (selected) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(8.dp)) {
                    repeat(5) { i ->
                        val barHeight = (4 + 4 * kotlin.math.sin(wavePhase.toDouble() + i * 0.8).toFloat()).dp
                        Box(modifier = Modifier.width(2.dp).height(barHeight).clip(RoundedCornerShape(1.dp)).background(accent.copy(alpha = 0.7f)))
                    }
                }
            } else { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun RowScope.ThemeOption(label: String, emoji: String, previewTop: Color, previewBottom: Color, selected: Boolean, onClick: () -> Unit, isSystem: Boolean = false, modifier: Modifier = Modifier) {
    val accent = LocalAccentColor.current
    val haptic = LocalHapticFeedback.current
    Surface(modifier = modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }, shape = RoundedCornerShape(14.dp), color = if (selected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), border = if (selected) BorderStroke(1.5.dp, accent.copy(alpha = 0.40f)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(44.dp, 30.dp).clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(listOf(previewTop, previewBottom)))) {
                if (isSystem) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1A1A24)))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFF8F9FC)))
                    }
                }
            }
            Text("$emoji $label", fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun FuturisticSwitch(checked: Boolean, onCheckedChange: () -> Unit) {
    val accent = LocalAccentColor.current
    Surface(onClick = onCheckedChange, shape = RoundedCornerShape(12.dp), color = if (checked) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), border = BorderStroke(1.5.dp, if (checked) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), modifier = Modifier.size(46.dp, 24.dp)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(modifier = Modifier.padding(3.dp).size(18.dp).clip(CircleShape).background(if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun DangerButton(icon: ImageVector, label: String, subtitle: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.05f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
            Column {
                Text(label, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) { Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
            }
        }
    }
}

@Composable
private fun MinimalIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)), modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) { content() }
        }
    }
}

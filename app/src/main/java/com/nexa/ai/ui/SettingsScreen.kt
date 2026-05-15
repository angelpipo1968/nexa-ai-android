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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.ui.theme.dynamicPrimaryColor
import com.nexa.ai.viewmodel.*

/** CompositionLocal providing the effective accent color for the current theme. */
val LocalAccentColor = compositionLocalOf { NexaAccent }

// ═══════════════════════════════════════════════════════════════
//  SETTINGS SCREEN — Minimalist Futuristic Redesign
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
    onLogout: () -> Unit
) {
    // Effective accent: dynamic for SYSTEM, custom neon for DARK/LIGHT
    val effectiveAccent = if (uiState.themeMode == ThemeMode.SYSTEM) dynamicPrimaryColor() else NexaAccent

    CompositionLocalProvider(LocalAccentColor provides effectiveAccent) {

    // Ambient glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(4000, easing = EaseInOut), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Ambient glow orbs ──
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset((-50).dp, (-100).dp)
                .blur(120.dp)
                .background(effectiveAccent.copy(alpha = glowAlpha))
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .offset(200.dp, 500.dp)
                .blur(120.dp)
                .background(Color(0xFF0066FF).copy(alpha = glowAlpha * 0.5f))
        )

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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                                modifier = Modifier.size(18.dp))
                        }
                    },
                    actions = {
                        MinimalIconButton(onClick = { /* future settings */ }) {
                            Icon(Icons.Default.Settings, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            // Staggered entrance animation state
            var sectionsVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { sectionsVisible = true }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // ── Brand (staggered index 0) ──
                StaggeredFadeIn(visible = sectionsVisible, index = 0) { Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(effectiveAccent, effectiveAccent.copy(alpha = 0.7f))))
                            .drawBehind {
                                drawRoundRect(
                                    color = effectiveAccent.copy(alpha = 0.3f),
                                    cornerRadius = CornerRadius(10.dp.toPx()),
                                    size = size,
                                    topLeft = Offset.Zero
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null,
                            modifier = Modifier.size(18.dp), tint = Color.Black)
                    }
                    Text(
                        "NEXA",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        color = effectiveAccent
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = effectiveAccent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "PRO",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = effectiveAccent
                        )
                    }
                }
                } // StaggeredFadeIn brand

                // ════════════════════════════════
                //  LANGUAGE (staggered index 1)
                // ════════════════════════════════
                StaggeredFadeIn(visible = sectionsVisible, index = 1) {
                SectionLabel(NexaStrings.get("language", uiState.language).uppercase())
                FuturisticCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            AppLanguage.SPANISH to "🇪🇸  Español",
                            AppLanguage.ENGLISH to "🇺🇸  English"
                        ).forEach { (lang, label) ->
                            val selected = uiState.language == lang
                            FuturisticPill(
                                label = label,
                                selected = selected,
                                accent = effectiveAccent,
                                modifier = Modifier.weight(1f),
                                onClick = { onSetLanguage(lang) }
                            )
                        }
                    }
                }
                } // StaggeredFadeIn language

                // ════════════════════════════════
                //  VOICE (staggered index 2)
                // ════════════════════════════════
                StaggeredFadeIn(visible = sectionsVisible, index = 2) {
                SectionLabel(NexaStrings.get("voice", uiState.language).uppercase())
                FuturisticCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                NexaStrings.get("voice", uiState.language),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            NexaStrings.get(uiState.voiceType.name.lowercase(), uiState.language),
                            fontSize = 10.sp,
                            color = effectiveAccent.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    // Male section
                    Text(
                        NexaStrings.get("male_label", uiState.language),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VoiceType.entries.filter { it.name.contains("MALE") }.forEach { voice ->
                            val selected = uiState.voiceType == voice
                            VoiceCard(
                                voice = voice,
                                label = NexaStrings.get(voice.name.lowercase(), uiState.language),
                                selected = selected,
                                modifier = Modifier.weight(1f),
                                onClick = { onSetVoiceType(voice) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Female section
                    Text(
                        NexaStrings.get("female_label", uiState.language),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VoiceType.entries.filter { it.name.contains("FEMALE") }.forEach { voice ->
                            val selected = uiState.voiceType == voice
                            VoiceCard(
                                voice = voice,
                                label = NexaStrings.get(voice.name.lowercase(), uiState.language),
                                selected = selected,
                                modifier = Modifier.weight(1f),
                                onClick = { onSetVoiceType(voice) }
                            )
                        }
                    }
                }
                } // StaggeredFadeIn voice

                // ════════════════════════════════
                //  THEME (staggered index 3)
                // ════════════════════════════════
                StaggeredFadeIn(visible = sectionsVisible, index = 3) {
                SectionLabel(NexaStrings.get("theme", uiState.language).uppercase())
                FuturisticCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeOption(
                            label = NexaStrings.get("dark", uiState.language),
                            emoji = "🌙",
                            previewTop = Color(0xFF1A1A24),
                            previewBottom = Color(0xFF0D0D12),
                            selected = uiState.themeMode == ThemeMode.DARK,
                            onClick = { onSetThemeMode(ThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOption(
                            label = NexaStrings.get("light", uiState.language),
                            emoji = "☀️",
                            previewTop = Color(0xFFF8F9FC),
                            previewBottom = Color(0xFFFFFFFF),
                            selected = uiState.themeMode == ThemeMode.LIGHT,
                            onClick = { onSetThemeMode(ThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOption(
                            label = NexaStrings.get("system", uiState.language),
                            emoji = "⚙️",
                            previewTop = Color(0xFF1A1A24),
                            previewBottom = Color(0xFFF8F9FC),
                            selected = uiState.themeMode == ThemeMode.SYSTEM,
                            onClick = { onSetThemeMode(ThemeMode.SYSTEM) },
                            isSystem = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                } // StaggeredFadeIn theme

                // ════════════════════════════════
                //  PREFERENCES (staggered index 4)
                // ════════════════════════════════
                StaggeredFadeIn(visible = sectionsVisible, index = 4) {
                SectionLabel(
                    NexaStrings.get("preferences", uiState.language)
                )
                FuturisticCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.VolumeUp, null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column {
                                Text(
                                    NexaStrings.get("auto_speak", uiState.language),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    NexaStrings.get("auto_speak_desc", uiState.language),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                        FuturisticSwitch(
                            checked = uiState.autoSpeak,
                            onCheckedChange = { onToggleAutoSpeak() }
                        )
                    }
                }
                } // StaggeredFadeIn preferences

                // ════════════════════════════════
                //  DANGER ZONE (staggered index 5)
                // ════════════════════════════════
                StaggeredFadeIn(visible = sectionsVisible, index = 5) {
                SectionLabel(
                    NexaStrings.get("danger_zone", uiState.language),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                )
                FuturisticCard {
                    // Clear chat
                    DangerButton(
                        icon = Icons.Default.Delete,
                        label = NexaStrings.get("clear_chat", uiState.language),
                        onClick = onClearChat
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Login / Logout
                    if (uiState.user.isLoggedIn) {
                        DangerButton(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            label = NexaStrings.get("logout", uiState.language),
                            subtitle = uiState.user.email,
                            onClick = onLogout
                        )
                    } else {
                        val accent = LocalAccentColor.current
                        Surface(
                            onClick = onNavigateToLogin,
                            shape = RoundedCornerShape(14.dp),
                            color = accent.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(accent.copy(alpha = 0.10f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, null,
                                        modifier = Modifier.size(15.dp), tint = accent)
                                }
                                Text(
                                    NexaStrings.get("login", uiState.language),
                                    color = accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
                } // StaggeredFadeIn danger zone

                // ── Version (staggered index 6) ──
                StaggeredFadeIn(visible = sectionsVisible, index = 6) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), Color.Transparent)
                                )
                            )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "NEXA PRO v${com.nexa.ai.BuildConfig.VERSION_NAME}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                }
                } // StaggeredFadeIn version

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
    } // CompositionLocalProvider
}

// ═══════════════════════════════════════════════════════════════
//  COMPONENTS — Minimalist Futuristic
// ═══════════════════════════════════════════════════════════════

@Composable
private fun StaggeredFadeIn(
    visible: Boolean,
    index: Int,
    durationMs: Int = 450,
    staggerMs: Int = 80,
    content: @Composable () -> Unit
) {
    val animVisibleState = remember { MutableTransitionState(false) }
    animVisibleState.targetState = visible

    AnimatedVisibility(
        visibleState = animVisibleState,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = durationMs,
                delayMillis = index * staggerMs,
                easing = FastOutSlowInEasing
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = durationMs,
                delayMillis = index * staggerMs,
                easing = FastOutSlowInEasing
            ),
            initialOffsetY = { it / 12 }
        ),
        exit = fadeOut()
    ) {
        content()
    }
}

@Composable
private fun SectionLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 3.sp,
        color = color,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun FuturisticCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            content()
        }
    }
}

@Composable
private fun FuturisticPill(
    label: String,
    selected: Boolean,
    accent: Color = NexaAccent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // Press scale animation
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pillScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.07f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        border = if (selected) BorderStroke(1.5.dp, accent.copy(alpha = 0.30f))
        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (selected) {
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                listOf(accent.copy(alpha = 0.06f), Color.Transparent)
                            ),
                            cornerRadius = CornerRadius(12.dp.toPx()),
                            size = size
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                letterSpacing = 0.3.sp
            )
        }
    }
}

@Composable
private fun VoiceCard(
    voice: VoiceType,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = LocalAccentColor.current
    val haptic = LocalHapticFeedback.current
    val isMale = voice.name.contains("MALE")

    // Press scale animation
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "voiceScale"
    )

    // Waveform animation for selected card
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "wavePhase"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                )
            },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = 0.05f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
        border = if (selected) BorderStroke(1.5.dp, accent.copy(alpha = 0.35f))
        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
    ) {
        Box(
            modifier = if (selected) Modifier.drawBehind {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.08f), Color.Transparent)
                    ),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    size = size
                )
            } else Modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) accent.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person, null,
                        modifier = Modifier.size(16.dp),
                        tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Text(
                    label,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.3.sp
                )
                // Active waveform / dot
                if (selected) {
                    // Mini neon waveform
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(8.dp)
                    ) {
                        repeat(5) { i ->
                            val barHeight = (4 + 4 * kotlin.math.sin(
                                wavePhase.toDouble() + i * 0.8
                            ).toFloat()).dp
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(accent.copy(alpha = 0.7f))
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun RowScope.ThemeOption(
    label: String,
    emoji: String,
    previewTop: Color,
    previewBottom: Color,
    selected: Boolean,
    onClick: () -> Unit,
    isSystem: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = 0.05f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
        border = if (selected) BorderStroke(1.5.dp, accent.copy(alpha = 0.35f))
        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
    ) {
        Box(
            modifier = if (selected) Modifier.drawBehind {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.06f), Color.Transparent)
                    ),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    size = size
                )
            } else Modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 8.dp),
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
                            // Accent bar
                            drawRoundRect(
                                color = accent.copy(alpha = 0.4f),
                                topLeft = Offset(4.dp.toPx(), 3.dp.toPx()),
                                size = Size(size.width - 8.dp.toPx(), 1.5.dp.toPx()),
                                cornerRadius = CornerRadius(0.75.dp.toPx())
                            )
                            // Content lines
                            drawRoundRect(
                                color = accent.copy(alpha = 0.12f),
                                topLeft = Offset(4.dp.toPx(), 7.5.dp.toPx()),
                                size = Size(size.width * 0.55f, 1.dp.toPx()),
                                cornerRadius = CornerRadius(0.5.dp.toPx())
                            )
                            drawRoundRect(
                                color = accent.copy(alpha = 0.08f),
                                topLeft = Offset(4.dp.toPx(), 10.dp.toPx()),
                                size = Size(size.width * 0.4f, 1.dp.toPx()),
                                cornerRadius = CornerRadius(0.5.dp.toPx())
                            )
                        }
                ) {
                    if (isSystem) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(
                                Brush.horizontalGradient(listOf(
                                    Color(0xFF1A1A24),
                                    Color(0xFF1A1A24).copy(alpha = 0.7f)
                                ))
                            ))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(
                                Brush.horizontalGradient(listOf(
                                    Color(0xFFF8F9FC).copy(alpha = 0.7f),
                                    Color(0xFFF8F9FC)
                                ))
                            ))
                        }
                    }
                }

                Text(
                    "$emoji $label",
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.3.sp
                )

                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun FuturisticSwitch(
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    val accent = LocalAccentColor.current
    Surface(
        onClick = onCheckedChange,
        shape = RoundedCornerShape(12.dp),
        color = if (checked) accent.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            1.5.dp,
            if (checked) accent.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        ),
        modifier = Modifier.size(44.dp, 24.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    .drawBehind {
                        if (checked) {
                            drawCircle(
                                color = accent.copy(alpha = 0.3f),
                                radius = size.maxDimension * 0.8f
                            )
                        }
                    }
            )
        }
    }
}

@Composable
private fun DangerButton(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.04f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.55f))
            }
            Column {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.60f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimalIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ) {
                content()
            }
        }
    }
}

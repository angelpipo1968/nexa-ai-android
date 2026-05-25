import re

with open(r"c:\NexaIA\app\src\main\java\com\nexa\ai\ui\ChatScreen.kt", "r", encoding="utf-8") as f:
    code = f.read()

# Add imports
imports_to_add = """
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.*
"""
# find package declaration and insert imports after it
if "import androidx.compose.ui.draw.scale" not in code:
    code = re.sub(r"(package com\.nexa\.ai\.ui\n)", r"\1" + imports_to_add, code)


new_overlay = """@Composable
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
        when (state) {
            "Speaking" -> Color(0xFF00E5FF)
            "Listening" -> Color(0xFFAA00FF)
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
}"""

pattern = r"@Composable\s*fun VoiceModeOverlay\([\s\S]*?(?=@Composable|$)"
code = re.sub(pattern, new_overlay + "\n\n", code)

with open(r"c:\NexaIA\app\src\main\java\com\nexa\ai\ui\ChatScreen.kt", "w", encoding="utf-8") as f:
    f.write(code)

print("ChatScreen.kt fixed!")

package com.nexa.ai.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartScreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.ui.theme.LocalAccentColor
import com.nexa.ai.viewmodel.NexaUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSettingsScreen(
    uiState: NexaUiState,
    onBack: () -> Unit,
    onToggleLocalLLM: (Boolean) -> Unit,
    onToggleSync: (Boolean) -> Unit,
    onSetMaxTokens: (Int) -> Unit,
    onDownloadModel: () -> Unit,
    onSetLocalLlmBaseUrl: (String) -> Unit = {},
    onSetLocalVisionModel: (String) -> Unit = {},
    onSetLocalChatModel: (String) -> Unit = {}
) {
    val effectiveAccent = LocalAccentColor.current
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(4000, easing = EaseInOut), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient blurs for premium look
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
                .offset(200.dp, 400.dp)
                .blur(120.dp)
                .background(Color(0xFF0066FF).copy(alpha = glowAlpha * 0.5f))
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "CONFIGURACIÓN OFFLINE",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Info privacy banner
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(effectiveAccent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, null, tint = effectiveAccent, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Privacidad Absoluta",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Tu voz y tus imágenes nunca salen del dispositivo. La IA local procesa todo sin internet.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Main controls card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Switch 1: Usar IA local
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(effectiveAccent.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SmartScreen, null, tint = effectiveAccent, modifier = Modifier.size(16.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Usar IA local", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("La IA funciona offline en tu dispositivo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = uiState.useLocalLLM,
                                onCheckedChange = onToggleLocalLLM,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = effectiveAccent,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Switch 2: Permitir sincronización
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(effectiveAccent.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudQueue, null, tint = effectiveAccent, modifier = Modifier.size(16.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Permitir sincronización en línea", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Sincroniza datos cuando haya internet", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = uiState.allowSync,
                                onCheckedChange = onToggleSync,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = effectiveAccent,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Slider: Tokens máximos
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Tokens máximos", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${uiState.maxTokens} tkn", fontWeight = FontWeight.Bold, color = effectiveAccent, fontSize = 12.sp)
                            }
                            Slider(
                                value = uiState.maxTokens.toFloat(),
                                onValueChange = { onSetMaxTokens(it.toInt()) },
                                valueRange = 64f..1024f,
                                steps = 15,
                                colors = SliderDefaults.colors(
                                    thumbColor = effectiveAccent,
                                    activeTrackColor = effectiveAccent,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                            Text(
                                "Limita la longitud máxima de las respuestas generadas en modo offline.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // ── LiteLLM Configuration Card ──
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Servidor LiteLLM Local",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Conecta tu app al servidor LiteLLM en tu red local (RTX 3090). El chat usa el modelo de texto y la cámara usa el modelo de visión (VLM).",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        // Base URL
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("URL del servidor LiteLLM", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            var baseUrl by remember(uiState.localLlmBaseUrl) { mutableStateOf(uiState.localLlmBaseUrl) }
                            OutlinedTextField(
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("http://192.168.1.50:4000", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                trailingIcon = {
                                    TextButton(onClick = { onSetLocalLlmBaseUrl(baseUrl) }) {
                                        Text("Guardar", fontSize = 11.sp, color = effectiveAccent)
                                    }
                                }
                            )
                            Text(
                                "IMPORTANTE: Siempre puerto 4000 (LiteLLM router). NO uses 8002 (vLLM) ni 3000 (Next.js UI).",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Chat Model
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Modelo de Chat (texto)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            var chatModel by remember(uiState.localChatModel) { mutableStateOf(uiState.localChatModel) }
                            OutlinedTextField(
                                value = chatModel,
                                onValueChange = { chatModel = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("qwen", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                trailingIcon = {
                                    TextButton(onClick = { onSetLocalChatModel(chatModel) }) {
                                        Text("Guardar", fontSize = 11.sp, color = effectiveAccent)
                                    }
                                }
                            )
                            Text(
                                "Modelo de solo texto configurado en litellm_config.yaml (ej: qwen, qwen2.5-7b-awq).",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        // Vision Model - VLM Selector
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Modelo de Visión (VLM)", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                            // Predefined VLM model options
                            val vlmOptions = listOf(
                                "vision" to "llava:7b (Rápido, liviano)",
                                "qwen-vision" to "Qwen2.5-VL (Mejor calidad, OCR)",
                                "phi-vision" to "Phi-3-Vision (Más rápido)"
                            )
                            var selectedVlm by remember(uiState.localVisionModel) {
                                mutableStateOf(vlmOptions.indexOfFirst { it.first == uiState.localVisionModel }.takeIf { it >= 0 } ?: 0)
                            }
                            var customVlm by remember(uiState.localVisionModel) {
                                mutableStateOf(if (vlmOptions.any { it.first == uiState.localVisionModel }) "" else uiState.localVisionModel)
                            }

                            vlmOptions.forEachIndexed { index, (modelId, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .then(
                                            if (selectedVlm == index) Modifier.background(effectiveAccent.copy(alpha = 0.08f))
                                            else Modifier
                                        )
                                        .clickable {
                                            selectedVlm = index
                                            customVlm = ""
                                            onSetLocalVisionModel(modelId)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    RadioButton(
                                        selected = selectedVlm == index,
                                        onClick = {
                                            selectedVlm = index
                                            customVlm = ""
                                            onSetLocalVisionModel(modelId)
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = effectiveAccent,
                                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("Modelo: $modelId", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }

                            // Custom model option
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(
                                        if (selectedVlm == vlmOptions.size) Modifier.background(effectiveAccent.copy(alpha = 0.08f))
                                        else Modifier
                                    )
                                    .clickable { selectedVlm = vlmOptions.size }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedVlm == vlmOptions.size,
                                    onClick = { selectedVlm = vlmOptions.size },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = effectiveAccent,
                                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Personalizado:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            if (selectedVlm == vlmOptions.size) {
                                OutlinedTextField(
                                    value = customVlm,
                                    onValueChange = { customVlm = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Nombre del modelo en litellm_config.yaml", fontSize = 11.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                    trailingIcon = {
                                        TextButton(onClick = { onSetLocalVisionModel(customVlm) }) {
                                            Text("Guardar", fontSize = 11.sp, color = effectiveAccent)
                                        }
                                    }
                                )
                            }

                            Text(
                                "Debe estar configurado en litellm_config.yaml. Streaming habilitado para todos los modelos.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Model download card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Modelo de IA Local (GGUF)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Para que la IA funcione 100% offline, necesitas descargar o inicializar los pesos del modelo Llama-3 en el almacenamiento interno de tu dispositivo (aprox. 4.2 GB).",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        if (uiState.isDownloadingModel) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Descargando...", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("${(uiState.modelDownloadProgress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = effectiveAccent)
                                }
                                LinearProgressIndicator(
                                    progress = uiState.modelDownloadProgress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = effectiveAccent,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        } else {
                            Button(
                                onClick = onDownloadModel,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = effectiveAccent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Download, null, tint = Color.Black)
                                    Text("DESCARGAR MODELO OFFLINE", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Footer
                Text(
                    "Puedes cambiar estas configuraciones de privacidad y almacenamiento en cualquier momento.",
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
            }
        }
    }
}

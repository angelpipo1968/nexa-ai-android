package com.nexa.ai.ui

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.ui.AdaptivePadding
import com.nexa.ai.ui.adaptiveText
import com.nexa.ai.viewmodel.AppLanguage
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

// ═══════════════════════════════════════════════════════════════
//  LIVE TRANSLATOR SCREEN — Real-time voice-to-voice translation
// ═══════════════════════════════════════════════════════════════

data class LangOption(val code: String, val name: String, val flag: String, val locale: Locale)

val LANGUAGES = listOf(
    LangOption("es", "Español", "🇪🇸", Locale("es", "ES")),
    LangOption("en", "English", "🇺🇸", Locale.US),
    LangOption("pt", "Português", "🇧🇷", Locale("pt", "BR")),
    LangOption("fr", "Français", "🇫🇷", Locale.FRENCH),
    LangOption("de", "Deutsch", "🇩🇪", Locale.GERMAN),
    LangOption("it", "Italiano", "🇮🇹", Locale.ITALIAN),
    LangOption("zh", "中文", "🇨🇳", Locale.CHINESE),
    LangOption("ja", "日本語", "🇯🇵", Locale.JAPANESE),
    LangOption("ko", "한국어", "🇰🇷", Locale.KOREAN),
    LangOption("ru", "Русский", "🇷🇺", Locale("ru", "RU")),
    LangOption("ar", "العربية", "🇸🇦", Locale("ar", "SA")),
    LangOption("hi", "हिन्दी", "🇮🇳", Locale("hi", "IN")),
    LangOption("tr", "Türkçe", "🇹🇷", Locale("tr", "TR")),
    LangOption("th", "ไทย", "🇹🇭", Locale("th", "TH")),
    LangOption("vi", "Tiếng Việt", "🇻🇳", Locale("vi", "VN")),
    LangOption("id", "Indonesian", "🇮🇩", Locale("id", "ID")),
    LangOption("nl", "Nederlands", "🇳🇱", Locale("nl", "NL")),
    LangOption("pl", "Polski", "🇵🇱", Locale("pl", "PL")),
    LangOption("sv", "Svenska", "🇸🇪", Locale("sv", "SE")),
    LangOption("uk", "Українська", "🇺🇦", Locale("uk", "UA")),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen(
    
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // State
    var sourceLang by remember { mutableStateOf(LANGUAGES[0]) }  // Spanish
    var targetLang by remember { mutableStateOf(LANGUAGES[1]) }  // English
    var sourceText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var showSourceLangPicker by remember { mutableStateOf(false) }
    var showTargetLangPicker by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    // Speech
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) { /* ready */ }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
            tts.stop()
            tts.shutdown()
        }
    }

    // Translation function
    fun translate(text: String, from: String, to: String) {
        if (text.isBlank()) return
        isTranslating = true
        scope.launch(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(text, "UTF-8")
                val url = URL("https://api.mymemory.translated.net/get?q=$encoded&langpair=$from|$to")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val result = json.getJSONObject("responseData").getString("translatedText")
                withContext(Dispatchers.Main) {
                    translatedText = result
                    isTranslating = false
                    history = listOf(text to result) + history.take(19)
                    // Auto-speak translation
                    tts.language = targetLang.locale
                    tts.speak(result, TextToSpeech.QUEUE_FLUSH, null, "translator")
                    isSpeaking = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    translatedText = "Error: ${e.message}"
                    isTranslating = false
                }
            }
        }
    }

    // Speech recognition listener
    LaunchedEffect(Unit) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return
                sourceText = text
                translate(text, sourceLang.code, targetLang.code)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        tts.stop()
        isSpeaking = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLang.locale.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        speechRecognizer.startListening(intent)
    }

    fun swapLanguages() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val temp = sourceLang
        sourceLang = targetLang
        targetLang = temp
        sourceText = translatedText.also { translatedText = sourceText }
    }

    // UI
    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        // Top Bar
        TopAppBar(
            title = { Text("🌍 Traductor en Vivo", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Language selector bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source language
                LanguageChip(
                    lang = sourceLang,
                    onClick = { showSourceLangPicker = true }
                )

                // Swap button
                IconButton(
                    onClick = { swapLanguages() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NexaAccent.copy(alpha = 0.15f))
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        "Swap",
                        tint = NexaAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Target language
                LanguageChip(
                    lang = targetLang,
                    onClick = { showTargetLangPicker = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Source text card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${sourceLang.flag} ${sourceLang.name}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        sourceText.ifBlank { "Toca el micrófono para hablar..." },
                        fontSize = adaptiveText(18.sp),
                        fontWeight = if (sourceText.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                        color = if (sourceText.isNotBlank()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Translated text card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = NexaAccent.copy(alpha = 0.08f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${targetLang.flag} ${targetLang.name}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = NexaAccent
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        translatedText.ifBlank { "Traducción aparecerá aquí..." },
                        fontSize = adaptiveText(18.sp),
                        fontWeight = if (translatedText.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                        color = if (translatedText.isNotBlank()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )

                    // Speak button
                    if (translatedText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.End) {
                            IconButton(
                                onClick = {
                                    tts.language = targetLang.locale
                                    tts.speak(translatedText, TextToSpeech.QUEUE_FLUSH, null, "replay")
                                    isSpeaking = true
                                }
                            ) {
                                Icon(
                                    if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    "Speak",
                                    tint = NexaAccent
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Microphone button
            val pulseAnim = rememberInfiniteTransition(label = "pulse")
            val pulseScale by pulseAnim.animateFloat(
                initialValue = 1f,
                targetValue = if (isListening) 1.15f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )

            Box(contentAlignment = Alignment.Center) {
                // Outer ring when listening
                if (isListening) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(NexaAccent.copy(alpha = 0.15f))
                    )
                }

                IconButton(
                    onClick = { startListening() },
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (isListening) NexaAccent
                            else NexaAccent.copy(alpha = 0.2f)
                        )
                ) {
                    Icon(
                        if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                        "Speak",
                        tint = if (isListening) Color.White else NexaAccent,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                when {
                    isListening -> "🎙️ Escuchando..."
                    isTranslating -> "🔄 Traduciendo..."
                    isSpeaking -> "🔊 Reproduciendo..."
                    else -> "Toca para hablar"
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // History
            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Historial",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                history.forEach { (src, dst) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(src, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("→ $dst", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // Language picker dialogs
    if (showSourceLangPicker) {
        LanguagePickerDialog(
            title = "Idioma origen",
            onSelect = { sourceLang = it; showSourceLangPicker = false },
            onDismiss = { showSourceLangPicker = false }
        )
    }
    if (showTargetLangPicker) {
        LanguagePickerDialog(
            title = "Idioma destino",
            onSelect = { targetLang = it; showTargetLangPicker = false },
            onDismiss = { showTargetLangPicker = false }
        )
    }
}

@Composable
fun LanguageChip(lang: LangOption, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, NexaAccent.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(lang.flag, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(lang.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LanguagePickerDialog(title: String, onSelect: (LangOption) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                LANGUAGES.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(lang) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lang.flag, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(lang.name, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

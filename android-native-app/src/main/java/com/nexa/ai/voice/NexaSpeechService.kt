package com.nexa.ai.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Build
import android.util.Log
<<<<<<< Updated upstream
import com.nexa.ai.MainActivity
=======
import com.nexa.ai.debug.TraeDebug
>>>>>>> Stashed changes
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * NexaSpeechService — Servicio de voz Android de grado de producción.
 *
 * CRITICAL FIX v5.4: This service now uses Hilt injection to get the SAME
 * SpeechManager singleton that the ViewModel uses. Previously, this service
 * created its own SpeechManager instance, causing:
 * - TWO TTS engines fighting for audio output
 * - TWO SpeechRecognizers competing for the microphone
 * - "Speech not connected" errors because the service's SpeechManager
 *   was a different instance than the ViewModel's
 *
 * Architecture:
 * - Uses @AndroidEntryPoint for Hilt DI
 * - Injects the same @Singleton SpeechManager from AppModule
 * - Only serves as foreground service (keeps app alive during voice mode)
 * - Does NOT create duplicate audio sessions or recognizers
 * - SpeechManager callbacks are managed by the ViewModel, not by this service
 */
@AndroidEntryPoint
class NexaSpeechService : Service() {

    private val TAG = "NexaSpeechService"
    private val binder = SpeechBinder()
<<<<<<< Updated upstream
    private val NOTIFICATION_CHANNEL_ID = "nexa_voice_channel"
    private val NOTIFICATION_ID = 1001

    // CRITICAL FIX: Inject the SAME SpeechManager singleton that the ViewModel uses.
    // Before this fix, the service created its own SpeechManager(application) which
    // was a completely separate instance, causing duplicate TTS engines and
    // SpeechRecognizers fighting for the microphone.
=======
    
    // Instancia persistente del gestor de voz
>>>>>>> Stashed changes
    @Inject
    lateinit var speechManager: SpeechManager

    companion object {
        // Acciones de Broadcast para desacoplar el ViewModel de la UI
        const val ACTION_SPEECH_RESULT = "com.nexa.ai.action.SPEECH_RESULT"
        const val ACTION_SPEECH_PARTIAL = "com.nexa.ai.action.SPEECH_PARTIAL"
        const val ACTION_SPEECH_STATE = "com.nexa.ai.action.SPEECH_STATE"
        const val ACTION_SPEECH_ERROR = "com.nexa.ai.action.SPEECH_ERROR"

        // Extras
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_STATE = "extra_state"
        const val EXTRA_ERROR_KEY = "extra_error_key"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Iniciando NexaSpeechService")

<<<<<<< Updated upstream
        // Crear canal de notificación para Android 8+
        createNotificationChannel()

        // CRITICAL FIX: Do NOT create a new SpeechManager here!
        // The injected speechManager is the SAME singleton that the ViewModel uses.
        // We only need to initialize it if it hasn't been initialized yet.
        // The ViewModel calls speechManager.initialize() in its init block,
        // so by the time this service starts, it should already be initialized.
        // We still call initialize() as a safety net (it's idempotent for TTS).
        speechManager.initialize()

        Log.d(TAG, "Using SHARED SpeechManager singleton — no duplicate TTS/STT")
=======
        // Vinculamos los callbacks de SpeechManager con envíos de Broadcast
        setupSpeechManagerCallbacks()
>>>>>>> Stashed changes
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: Iniciando sesión de voz activa por intent")
<<<<<<< Updated upstream

        // FIX: Start as foreground service IMMEDIATELY to prevent Android from killing it.
        startForegroundNotification()

        // CRITICAL FIX: Do NOT call startVoiceAudioSession() or startListening() here!
        // The ViewModel already manages the full voice lifecycle:
        //   1. toggleVoiceMode() → speechManager.startVoiceAudioSession()
        //   2. toggleVoiceMode() → speechManager.startListening()
        // If we call these here too, we'd create duplicate audio sessions.
        // This service's ONLY job is to be a foreground service that keeps
        // the app alive during voice mode.
        sendSpeechStateBroadcast("ready")

        return START_STICKY
=======
        // #region debug-point A:service-start-command
        TraeDebug.event(
            hypothesisId = "A",
            location = "NexaSpeechService:onStartCommand",
            msg = "[DEBUG] speech service onStartCommand",
            dataJson = """{"startId":$startId}""",
        )
        // #endregion
        
        // Cuando el servicio es iniciado por el sistema o por el botón del volante,
        // arrancamos la sesión de audio vehicular y el reconocedor de voz.
        startSpeechListeningSession()
        
        return START_STICKY // Asegura que Android intente recrear el servicio si es purgado por RAM
>>>>>>> Stashed changes
    }

    /**
     * FIX v5.3: Creates and shows the foreground notification.
     * Required on API 31+ — without this, Android kills the service within 5 seconds.
     */
    private fun startForegroundNotification() {
        try {
<<<<<<< Updated upstream
            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("NEXA PRO")
                .setContentText("Sesión de voz manos libres activa")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()

            // Use FOREGROUND_SERVICE_TYPE_MICROPHONE on API 34+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.d(TAG, "Foreground service started with notification")
=======
            Log.d(TAG, "Arrancando sesión de audio manos libres y reconociendo...")
            // #region debug-point A:start-listening-session
            TraeDebug.event(
                hypothesisId = "A",
                location = "NexaSpeechService:startSpeechListeningSession",
                msg = "[DEBUG] start speech listening session",
                dataJson = """{"serviceCreated":true}""",
            )
            // #endregion
            speechManager.startVoiceAudioSession()
            speechManager.startListening()
            
            // Notificamos el estado mediante broadcast
            sendSpeechStateBroadcast("listening")
>>>>>>> Stashed changes
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground: ${e.message}", e)
        }
    }

<<<<<<< Updated upstream
    /**
     * Creates the notification channel required for Android 8+ (API 26+).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Sesión de Voz NEXA",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación de sesión de voz manos libres"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
=======
    private fun setupSpeechManagerCallbacks() {
        speechManager.onListeningStateChanged = { isListening ->
            sendSpeechStateBroadcast(if (isListening) "listening" else "idle")
        }

        speechManager.onSpeakingStateChanged = { isSpeaking, messageId ->
            sendSpeechStateBroadcast(if (isSpeaking) "speaking" else "idle")
        }

        speechManager.onSpeechResult = { text ->
            Log.i(TAG, "onSpeechResult: Texto capturado -> $text")
            // #region debug-point D:speech-result
            TraeDebug.event(
                hypothesisId = "D",
                location = "NexaSpeechService:onSpeechResult",
                msg = "[DEBUG] speech result captured",
                dataJson = """{"textLength":${text.length}}""",
            )
            // #endregion
            val intent = Intent(ACTION_SPEECH_RESULT).apply {
                putExtra(EXTRA_TEXT, text)
                setPackage(packageName) // Seguridad extra: restringe el broadcast a nuestra app
            }
            sendBroadcast(intent)
        }

        speechManager.onSpeechPartial = { partialText ->
            val intent = Intent(ACTION_SPEECH_PARTIAL).apply {
                putExtra(EXTRA_TEXT, partialText)
                setPackage(packageName)
            }
            sendBroadcast(intent)
        }

        speechManager.onError = { errorKey ->
            Log.e(TAG, "onError: Error en el SpeechRecognizer -> $errorKey")
            // #region debug-point A:speech-error
            TraeDebug.event(
                hypothesisId = "A",
                location = "NexaSpeechService:onError",
                msg = "[DEBUG] speech manager error",
                dataJson = """{"errorKey":"${errorKey.replace("\"", "\\\"")}"}""",
            )
            // #endregion
            sendSpeechErrorBroadcast(errorKey)
        }

        speechManager.onBargeInDetected = {
            Log.d(TAG, "onBargeInDetected: Interrupción por voz activa detectada")
            sendSpeechStateBroadcast("barge_in")
>>>>>>> Stashed changes
        }
    }

    private fun sendSpeechStateBroadcast(state: String) {
        val intent = Intent(ACTION_SPEECH_STATE).apply {
            putExtra(EXTRA_STATE, state)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind: Componente vinculado directamente")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind: Componente desvinculado")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
<<<<<<< Updated upstream
        Log.i(TAG, "onDestroy: Apagando servicio de voz")

        // CRITICAL FIX: Do NOT call speechManager.stopVoiceAudioSession() or destroy() here!
        // The ViewModel manages the SpeechManager lifecycle. If we destroy it here,
        // the ViewModel's SpeechManager reference becomes invalid because it's the SAME instance.
        // The ViewModel handles cleanup in its own onCleared() method.

        // Stop foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

=======
        Log.i(TAG, "onDestroy: Deteniendo sesión de voz activa")
        
        // Solo detenemos la sesión activa, NO destruimos el manager
        // porque es un Singleton compartido con el ViewModel
        speechManager.stopVoiceAudioSession()
        
>>>>>>> Stashed changes
        sendSpeechStateBroadcast("destroyed")
        super.onDestroy()
    }

    /**
     * Binder interno para permitir llamadas sincrónicas de componentes vinculados
     */
    inner class SpeechBinder : Binder() {
        fun getService(): NexaSpeechService = this@NexaSpeechService
        fun getSpeechManager(): SpeechManager = speechManager
    }
}

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
import com.nexa.ai.MainActivity

/**
 * NexaSpeechService — Servicio de voz Android de grado de producción.
 *
 * Arquitectura:
 * - Soporta inicio por Intent (startService) para activaciones persistentes del volante / Android Auto.
 * - Soporta vinculación por Binder (onBind) para comunicación directa con ViewModels/Activities.
 * - Integra y encapsula la lógica avanzada de SpeechManager (VAD, Proximity, BT SCO, Barge-in, Focus).
 * - Comunica los resultados y estados conversacionales mediante Local Broadcasts para un
 *   desacoplamiento total del ciclo de vida de la UI, eliminando los cuellos de botella y cortes de voz.
 *
 * FIX v5.3: Added startForeground() with notification to prevent Android from killing
 * the service within 5 seconds (ForegroundServiceDidNotStartInTimeException on API 31+).
 * Added foregroundServiceType="microphone" in manifest for API 34+ compatibility.
 */
class NexaSpeechService : Service() {

    private val TAG = "NexaSpeechService"
    private val binder = SpeechBinder()
    private val NOTIFICATION_CHANNEL_ID = "nexa_voice_channel"
    private val NOTIFICATION_ID = 1001
    
    // Instancia persistente del gestor de voz
    lateinit var speechManager: SpeechManager
        private set

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
        
        // Crear canal de notificación para Android 8+
        createNotificationChannel()
        
        // Instanciamos el administrador de voz con el contexto de aplicación
        speechManager = SpeechManager(application)
        speechManager.initialize()
        
        // Vinculamos los callbacks de SpeechManager con envíos de Broadcast
        setupSpeechManagerCallbacks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: Iniciando sesión de voz activa por intent")
        
        // FIX: Start as foreground service IMMEDIATELY to prevent Android from killing it.
        // This MUST happen within 5 seconds of startForegroundService() on API 31+.
        startForegroundNotification()
        
        // Cuando el servicio es iniciado por el sistema o por el botón del volante,
        // arrancamos la sesión de audio vehicular y el reconocedor de voz.
        startSpeechListeningSession()
        
        return START_STICKY // Asegura que Android intente recrear el servicio si es purgado por RAM
    }

    /**
     * FIX v5.3: Creates and shows the foreground notification.
     * Required on API 31+ — without this, Android kills the service within 5 seconds
     * with ForegroundServiceDidNotStartInTimeException.
     */
    private fun startForegroundNotification() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground: ${e.message}", e)
        }
    }

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
        }
    }

    private fun startSpeechListeningSession() {
        try {
            // FIX: Only start the audio session here — do NOT call startListening()
            // because the ViewModel already manages the listening lifecycle.
            // Calling startListening() here creates a duplicate SpeechRecognizer
            // that competes with the ViewModel's instance for the microphone.
            speechManager.startVoiceAudioSession()
            // speechManager.startListening()  // REMOVED: ViewModel manages listening
            
            Log.d(TAG, "Sesión de audio manos libres iniciada (listening gestionado por ViewModel)")
            sendSpeechStateBroadcast("ready")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar la sesión de escucha: ${e.message}", e)
            sendSpeechErrorBroadcast("init_failed")
        }
    }

    private fun setupSpeechManagerCallbacks() {
        speechManager.onListeningStateChanged = { isListening ->
            sendSpeechStateBroadcast(if (isListening) "listening" else "idle")
        }

        speechManager.onSpeakingStateChanged = { isSpeaking, messageId ->
            sendSpeechStateBroadcast(if (isSpeaking) "speaking" else "idle")
        }

        speechManager.onSpeechResult = { text ->
            Log.i(TAG, "onSpeechResult: Texto capturado -> $text")
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
            sendSpeechErrorBroadcast(errorKey)
        }

        speechManager.onBargeInDetected = {
            Log.d(TAG, "onBargeInDetected: Interrupción por voz activa detectada")
            sendSpeechStateBroadcast("barge_in")
        }
    }

    private fun sendSpeechStateBroadcast(state: String) {
        val intent = Intent(ACTION_SPEECH_STATE).apply {
            putExtra(EXTRA_STATE, state)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun sendSpeechErrorBroadcast(errorKey: String) {
        val intent = Intent(ACTION_SPEECH_ERROR).apply {
            putExtra(EXTRA_ERROR_KEY, errorKey)
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
        Log.i(TAG, "onDestroy: Apagando servicio de voz y liberando hardware")
        
        // Apagamos la sesión de audio vehicular y liberamos los recursos de hardware
        speechManager.stopVoiceAudioSession()
        speechManager.destroy()
        
        // Stop foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        
        sendSpeechStateBroadcast("destroyed")
        super.onDestroy()
    }

    /**
     * Binder interno para permitir llamadas sincrónicas de componentes vinculados
     */
    inner class SpeechBinder : Binder() {
        fun getService(): NexaSpeechService = this@NexaSpeechService
    }
}

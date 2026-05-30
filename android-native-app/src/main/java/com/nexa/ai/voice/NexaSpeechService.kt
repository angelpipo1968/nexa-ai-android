package com.nexa.ai.voice

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * NexaSpeechService — Servicio de voz Android de grado de producción.
 *
 * Arquitectura:
 * - Soporta inicio por Intent (startService) para activaciones persistentes del volante / Android Auto.
 * - Soporta vinculación por Binder (onBind) para comunicación directa con ViewModels/Activities.
 * - Integra y encapsula la lógica avanzada de SpeechManager (VAD, Proximity, BT SCO, Barge-in, Focus).
 * - Comunica los resultados y estados conversacionales mediante Local Broadcasts para un
 *   desacoplamiento total del ciclo de vida de la UI, eliminando los cuellos de botella y cortes de voz.
 */
class NexaSpeechService : Service() {

    private val TAG = "NexaSpeechService"
    private val binder = SpeechBinder()
    
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
        
        // Instanciamos el administrador de voz con el contexto de aplicación
        speechManager = SpeechManager(application)
        speechManager.initialize()
        
        // Vinculamos los callbacks de SpeechManager con envíos de Broadcast
        setupSpeechManagerCallbacks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: Iniciando sesión de voz activa por intent")
        
        // Cuando el servicio es iniciado por el sistema o por el botón del volante,
        // arrancamos la sesión de audio vehicular y el reconocedor de voz.
        startSpeechListeningSession()
        
        return START_STICKY // Asegura que Android intente recrear el servicio si es purgado por RAM
    }

    private fun startSpeechListeningSession() {
        try {
            Log.d(TAG, "Arrancando sesión de audio manos libres y reconociendo...")
            speechManager.startVoiceAudioSession()
            speechManager.startListening()
            
            // Notificamos el estado mediante broadcast
            sendSpeechStateBroadcast("listening")
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

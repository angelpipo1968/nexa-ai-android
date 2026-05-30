package com.nexa.ai.voice

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

/**
 * NexaSpeechService — Un servicio persistente de Android (Bound & Started Service)
 * que aloja y mantiene vivo el SpeechManager.
 *
 * Ventajas:
 * 1. Evita que el SpeechRecognizer sea recolectado por el Garbage Collector (GC).
 * 2. Sobrevive de manera natural a los cambios de configuración y rotación de pantalla de las actividades.
 * 3. Mantiene estables las sesiones de audio, focus y Bluetooth SCO en segundo plano/Automotive.
 */
class NexaSpeechService : Service() {
    
    private val TAG = "NexaSpeechService"
    private val binder = SpeechBinder()
    
    // Instancia única y persistente de SpeechManager acoplada al ciclo de vida del Servicio
    lateinit var speechManager: SpeechManager
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Iniciando servicio persistente de reconocimiento de voz")
        
        // Creamos la instancia utilizando el contexto de la aplicación para evitar memory leaks
        speechManager = SpeechManager(application)
        speechManager.initialize()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: Servicio de voz marcado como START_STICKY")
        // Retornamos START_STICKY para asegurar que el sistema intente recrear el servicio si es matado por falta de RAM
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind: Componente vinculado al servicio de voz")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind: Componente desvinculado del servicio de voz")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: Destruyendo servicio persistente de voz y liberando recursos")
        speechManager.destroy()
        super.onDestroy()
    }

    /**
     * Binder de la clase para permitir que la Actividad o el ViewModel interactúe con el servicio
     */
    inner class SpeechBinder : Binder() {
        fun getService(): NexaSpeechService = this@NexaSpeechService
    }
}

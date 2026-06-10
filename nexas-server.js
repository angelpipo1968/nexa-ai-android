// Servidor simple para Nexas AI Assistant
// Este servidor maneja las respuestas del chat para la aplicación web

const express = require('express');
const cors = require('cors');
const path = require('path');

const app = express();
const PORT = 3001;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static('.'));

// Respuestas inteligentes basadas en categoría
const intelligentResponses = {
    general: [
        "Entiendo perfectamente tu punto. Con los fixes de manos libres v5.0 implementados en la app, la liberación de recursos de audio (`stopVoiceAudioSession`) ahora es instantánea y no causa ningún corte de volumen en la música de fondo.",
        "Excelente observación. La arquitectura híbrida de NexaIA está diseñada para equilibrar el procesamiento en la nube con modelos locales que corren directamente en tu NPU.",
        "Eso tiene mucho sentido. La optimización del barge-in (interrupción) en modo voz ahora cuenta con un cooldown adaptativo de 3.5 segundos que filtra el eco del altavoz sin perder nada de responsividad.",
        "Perfecto, voy a procesar esa información y te regreso con una propuesta detallada sobre cómo integrar estos componentes en tu flujo diario."
    ],
    coding: [
        "Analizando el código. En Kotlin, la llamada de red o procesamiento continuo de `AudioRecord` debe hacerse en un hilo secundario con `android.os.Process.setThreadPriority(THREAD_PRIORITY_URGENT_AUDIO)` para evitar el jank de la UI.",
        "Revisando los cambios. El error `NegativeArraySizeException` en Gradle/KSP es un problema clásico de corrupción de caché de Kotlin. Se resuelve ejecutando `./gradlew --stop` seguido de `./gradlew clean` para reconstruir la base de datos de símbolos.",
        "Para optimizar la comunicación por Bluetooth SCO, es crucial registrar un `BroadcastReceiver` que escuche `AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED` antes de forzar el modo de comunicación. ¿Quieres ver un ejemplo?",
        "Si estás programando en Jetpack Compose, recuerda usar `rememberUpdatedState` para los callbacks de voz en la UI, garantizando que el recomponer no pierda el estado de escucha."
    ],
    design: [
        "Desde el punto de vista de diseño, la retroalimentación visual en modo voz es crítica. Un indicador de volumen (Waveform) reactivo con el valor de `onVolumeLevelChanged` (normalizado entre 0 y 1) le da vida a la interfaz.",
        "¡Efectivamente! El Glassmorphism se ve increíble en el tema oscuro premium. Usar `backdrop-filter: blur(20px)` combinado con un borde semi-transparente de 1px añade una sensación de alta gama inmediata.",
        "Para pantallas móviles en el chat, es mejor mantener un diseño limpio con un área de input flotante y badges redondeados tipo píldora que se desplacen horizontalmente.",
        "Recomiendo usar animaciones `cubic-bezier(0.16, 1, 0.3, 1)` para la aparición de burbujas de chat. Se siente fluido, natural y sumamente moderno."
    ],
    voice: [
        "Iniciando diagnóstico del hands-free. Los fixes de manos libres corrigen el acople acústico en Samsung, Xiaomi y OPPO modificando el orden de apagado: liberando primero el micrófono y apagando el SCO después.",
        "Prueba de latencia: el tiempo de respuesta de interruptibilidad ha bajado de 2.5 segundos a tan solo 80ms gracias al ajuste fino de delays asíncronos en el `ViewModelScope`.",
        "El sensor de proximidad está integrado: apaga la pantalla y enruta el audio del altavoz al auricular del oído automáticamente si el dispositivo detecta que está cerca de tu oreja.",
        "¡Excelente! Hemos diagnosticado que el booster de volumen aumenta el volumen de 7 flujos de audio de forma progresiva, garantizando que escuches a Nexas incluso en ambientes con mucho ruido."
    ]
};

// Endpoint principal del chat
app.post('/api/chat', (req, res) => {
    try {
        const { message } = req.body;
        
        if (!message) {
            return res.status(400).json({ error: 'Mensaje requerido' });
        }

        // Detectar categoría del mensaje
        let category = 'general';
        const lowerMessage = message.toLowerCase();
        
        if (lowerMessage.includes('código') || lowerMessage.includes('program') || lowerMessage.includes('dev') || lowerMessage.includes('android') || lowerMessage.includes('kotlin')) {
            category = 'coding';
        } else if (lowerMessage.includes('diseño') || lowerMessage.includes('ui') || lowerMessage.includes('frontend') || lowerMessage.includes('visual') || lowerMessage.includes('css')) {
            category = 'design';
        } else if (lowerMessage.includes('voz') || lowerMessage.includes('audio') || lowerMessage.includes('micrófono') || lowerMessage.includes('manos libres') || lowerMessage.includes('hands free')) {
            category = 'voice';
        }

        // Obtener respuesta inteligente para la categoría
        const responses = intelligentResponses[category];
        const randomResponse = responses[Math.floor(Math.random() * responses.length)];

        // Simular procesamiento con respuesta inteligente
        const enhancedResponse = `${randomResponse}\n\n*Nota: Esta es una demostración del modo manos libres con respuestas inteligentes basadas en el contexto de tu mensaje.*`;

        res.json({ 
            response: enhancedResponse,
            category: category,
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        console.error('Error en el endpoint /api/chat:', error);
        res.status(500).json({ 
            error: 'Error interno del servidor',
            details: error.message 
        });
    }
});

// Endpoint de salud
app.get('/api/health', (req, res) => {
    res.json({ 
        status: 'ok', 
        message: 'Nexas AI Server funcionando',
        timestamp: new Date().toISOString(),
        version: '1.0.0'
    });
});

// Iniciar servidor
app.listen(PORT, () => {
    console.log(`🚀 Nexas AI Server corriendo en http://localhost:${PORT}`);
    console.log(`📡 Chat API disponible en: http://localhost:${PORT}/api/chat`);
    console.log(`🔍 Health check disponible en: http://localhost:${PORT}/api/health`);
    console.log(`🌐 Serviendo archivos estáticos desde: ${__dirname}`);
});

module.exports = app;
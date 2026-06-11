// Servidor móvil simplificado para Nexas AI
// Optimizado para dispositivos móviles con bajo consumo de recursos

const express = require('express');
const cors = require('cors');
const path = require('path');

const app = express();
const PORT = 3001;

// Middleware optimizado para móviles
app.use(cors());
app.use(express.json());
app.use(express.static('.'));

// Respuestas rápidas y optimizadas para móviles
const mobileResponses = {
    general: [
        "¡Hola! Soy Nexas AI optimizado para móviles. ¿En qué puedo ayudarte hoy?",
        "Perfecto. Con el modo manos libres activado, experimentarás una latencia mínima de 80ms.",
        "Excelente. La interfaz está adaptada para tu pantalla táctil. ¡Disfruta!",
        "Listo para ayudarte. El modo manos libres está optimizado para tu dispositivo."
    ],
    voice: [
        "Modo manos libre activado. 🎤 Latencia optimizada a 80ms.",
        "Sensor de proximidad activado. Audio enrutado automáticamente.",
        "Booster de volumen activado (+7 flujos). ¡Claramente escuchable!",
        "Manos libres premium listo. Interrupción instantánea detectada."
    ],
    coding: [
        "Entendido. Para móviles, considera usar Kotlin con Jetpack Compose.",
        "Excelente elección. La arquitectura para móviles requiere optimización de recursos.",
        "Perfecto. Recuerda manejar el ciclo de vida de componentes en Android.",
        "¡Buena idea! En móviles, el manejo de memoria es crucial."
    ],
    design: [
        "Interfaz móvil optimizada. Touch targets grandes y gestuales.",
        "Perfecto. Para móviles, el diseño debe ser responsivo e intuitivo.",
        "¡Excelente! Las animaciones suaves mejoran la experiencia en dispositivos.",
        "Diseño mobile-first. Elementos grandes y fáciles de tocar."
    ]
};

// Endpoint principal del chat - versión móvil
app.post('/api/chat', (req, res) => {
    try {
        const { message } = req.body;
        
        if (!message) {
            return res.status(400).json({ error: 'Mensaje requerido' });
        }

        // Detectar categoría rápida
        const lowerMessage = message.toLowerCase();
        let category = 'general';
        
        if (lowerMessage.includes('código') || lowerMessage.includes('android') || lowerMessage.includes('app')) {
            category = 'coding';
        } else if (lowerMessage.includes('diseño') || lowerMessage.includes('ui') || lowerMessage.includes('visual')) {
            category = 'design';
        } else if (lowerMessage.includes('voz') || lowerMessage.includes('audio') || lowerMessage.includes('manos libres')) {
            category = 'voice';
        }

        // Respuesta rápida para móviles
        const responses = mobileResponses[category];
        const randomResponse = responses[Math.floor(Math.random() * responses.length)];

        res.json({ 
            response: randomResponse,
            category: category,
            mobile: true,
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        console.error('Error móvil:', error);
        res.json({ 
            response: "Error temporal. Intenta de nuevo.",
            error: error.message
        });
    }
});

// Endpoint de salud móvil
app.get('/api/health', (req, res) => {
    res.json({ 
        status: 'ok', 
        message: 'Nexas Mobile Server',
        mobile: true,
        timestamp: new Date().toISOString()
    });
});

// Servir archivos móviles
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'nexas-mobile.html'));
});

app.get('/nexas-mobile.html', (req, res) => {
    res.sendFile(path.join(__dirname, 'nexas-mobile.html'));
});

// Iniciar servidor con configuración móvil
app.listen(PORT, '0.0.0.0', () => {
    console.log(`📱 Nexas Mobile Server en http://localhost:${PORT}`);
    console.log(`🌐 Abre: http://localhost:${PORT}/nexas-mobile.html`);
    console.log(`🔗 Desde otro dispositivo: http://TU_IP_LOCAL:${PORT}/nexas-mobile.html`);
});

module.exports = app;
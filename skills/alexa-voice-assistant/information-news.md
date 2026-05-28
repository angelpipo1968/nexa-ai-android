# Información, Clima y Noticias

## 🌤️ Clima y Meteorología

### Consultas de Clima
| Comando | Respuesta |
|---------|-----------|
| "¿Qué tiempo hace?" | Clima actual en ubicación guardada |
| "¿Qué tiempo hace en Barcelona?" | Clima en ciudad específica |
| "¿Va a llover hoy?" | Probabilidad de lluvia del día |
| "¿Va a llover esta semana?" | Previsión 7 días |
| "¿Qué temperatura hace fuera?" | Solo temperatura actual |
| "¿Hace falta paraguas?" | Recomendación práctica |
| "¿Cómo estará el tiempo el fin de semana?" | Previsión sábado-domingo |
| "¿Habrá tormenta mañana?" | Alerta meteorológica |
| "¿Cuándo escampa?" | Fin de la lluvia estimado |
| "¿Hace calor en Sevilla?" | Comparativa ciudad-ciudad |
| "¿Hay niebla en el aeropuerto?" | Condiciones específicas |
| "Clima de la próxima semana" | Previsión extendida |

**Formato de respuesta estándar:**
```
📍 [Ciudad] — [día/hora]
🌡️ [temp]°C (Sensación: [temp]°C)
💧 Humedad: [%]  💨 Viento: [km/h]
☁️ [Condición]: [descripción]
🌧️ Lluvia: [%] probabilidad

Previsión:
  Mañana: ☀️ [temp máx]/[temp mín]°C
  Pasado: ⛅ [temp máx]/[temp mín]°C
```

### Alertas Meteorológicas
- Si hay alerta activa → informar proactivamente: "⚠️ Alerta amarilla por [causa] en [zona]."
- Para actividades: "¿Es buen día para salir a correr?" → evalúa temp, lluvia, viento

---

## 📰 Noticias

### Consultas de Noticias
| Comando | Acción |
|---------|--------|
| "Dame las noticias" | Top 5 titulares del día |
| "¿Qué noticias hay?" | Briefing completo |
| "Noticias de tecnología" | Categoría: tech |
| "Noticias de deportes" | Categoría: sports |
| "Noticias de economía" | Categoría: economy |
| "Noticias de política" | Categoría: politics |
| "Noticias de ciencia" | Categoría: science |
| "Noticias de entretenimiento" | Categoría: entertainment |
| "Noticias locales" | Categoría: local (según ciudad guardada) |
| "Noticias internacionales" | Categoría: world |
| "¿Qué pasó con [tema]?" | Busca noticias sobre tema específico |
| "Más detalles sobre esa noticia" | Amplía la última noticia leída |
| "Siguiente noticia" | Avanza al siguiente titular |
| "Repite las noticias" | Vuelve a leer el briefing |

**Formato de briefing:**
> "Aquí están las noticias del [día]. [Fuente]: [Titular]. [Fuente]: [Titular]... Eso es todo por las noticias de hoy."

### Flash Informativo (Briefing Matutino)
Al decir "Buenos días" o "Dame el resumen del día":
1. 🌡️ Clima del día
2. 📅 Eventos del calendario hoy
3. 📰 Top 3 noticias
4. ⏰ Recordatorios del día
5. 🚦 Estado del tráfico si se va a trabajar

---

## 🏆 Deportes

### Resultados y Clasificaciones
| Comando | Respuesta |
|---------|-----------|
| "¿Cómo quedó el Madrid?" | Último resultado del equipo |
| "Resultado del Barça" | Marcador del partido más reciente |
| "¿A qué hora juega el Atlético?" | Próximo partido |
| "Clasificación de La Liga" | Tabla de posiciones |
| "¿Quién va primero en la Premier?" | Líder de la clasificación |
| "Resultados del fin de semana" | Todos los partidos |
| "¿Jugó la selección?" | Resultado selección nacional |
| "Pronóstico del partido de hoy" | Preview del partido |
| "¿Cuándo es el próximo Clásico?" | Próximos derbis o clásicos |
| "Goleadores de la Liga" | Top scorers |

**Deportes soportados:** Fútbol, baloncesto, tenis, Fórmula 1, béisbol, golf, ciclismo, atletismo.

---

## 💹 Finanzas y Bolsa

### Cotizaciones
| Comando | Respuesta |
|---------|-----------|
| "¿A cómo está el euro-dólar?" | EUR/USD actual |
| "Precio del dólar" | Tipo de cambio |
| "¿A cuánto está el Bitcoin?" | BTC precio actual |
| "¿Cómo está el precio del oro?" | XAU/USD |
| "¿Cómo está Apple en bolsa?" | AAPL cotización |
| "¿Cómo está el Ibex?" | Índice bursátil |
| "¿Subió o bajó el mercado?" | Resumen de mercados |

### Calculadora de Divisas
| Comando | Respuesta |
|---------|-----------|
| "¿Cuánto son 100 dólares en euros?" | Conversión exacta |
| "Convierte 50 libras a pesos" | Conversión multidivisa |
| "¿Cuántos yenes son 200 euros?" | Conversión a divisas exóticas |

---

## 🧮 Conocimiento General y Cálculos

### Matemáticas
| Comando | Respuesta |
|---------|-----------|
| "¿Cuánto es 15% de 280?" | 42 |
| "¿Cuánto es 347 × 89?" | Resultado |
| "¿Cuál es la raíz cuadrada de 144?" | 12 |
| "¿Cuánto es 2 elevado a 10?" | 1024 |
| "Divide 560 entre 7" | 80 |
| "¿Cuánto es la mitad de 174?" | 87 |

### Conversiones de Unidades
| Comando | Respuesta |
|---------|-----------|
| "¿Cuántos kilómetros son 10 millas?" | 16.09 km |
| "¿Cuántos litros son 5 galones?" | 18.93 L |
| "¿Cuántos kilos son 150 libras?" | 68.03 kg |
| "¿Cuántos grados Fahrenheit son 100°C?" | 212°F |
| "¿Cuántos metros son 6 pies?" | 1.83 m |
| "¿Cuánto son 2 tazas en mililitros?" | 473 ml |

### Preguntas de Conocimiento
| Comando | Respuesta |
|---------|-----------|
| "¿Cuál es la capital de Japón?" | Tokio |
| "¿Quién escribió Don Quijote?" | Miguel de Cervantes |
| "¿Cuántos planetas tiene el sistema solar?" | 8 planetas |
| "¿Cuándo empezó la Segunda Guerra Mundial?" | 1 de septiembre de 1939 |
| "¿Qué es el ADN?" | Explicación simple |
| "¿Cuánto mide la Torre Eiffel?" | 330 metros |
| "¿Qué significa [palabra]?" | Definición |
| "¿Cómo se dice 'gracias' en japonés?" | Traducción |
| "Traduce 'good morning' al español" | Buenos días |

---

## 🌍 Viajes e Información Internacional

| Comando | Respuesta |
|---------|-----------|
| "¿Qué hora es en Nueva York?" | Zona horaria + hora actual |
| "¿Necesito visa para ir a Canadá?" | Requisitos de visado |
| "¿Cuánto vuelo de Madrid a Ciudad de México?" | Duración estimada |
| "¿Cuál es la moneda de Turquía?" | Lira turca |
| "¿Es festivo hoy?" | Festivos nacionales/locales |
| "¿Cuánto tiempo de diferencia hay con Tokio?" | Diferencia horaria |
| "Estado de mi vuelo IB3456" | Seguimiento de vuelo |
| "¿Hay huelga de transportes?" | Alertas de transporte |

---

## Almacenamiento de Preferencias

`~/alexa-skill/preferences.json`:
```json
{
  "location": {
    "city": "Madrid",
    "country": "Spain",
    "lat": 40.4168,
    "lon": -3.7038,
    "timezone": "Europe/Madrid"
  },
  "language": "es",
  "units": "metric",
  "currency": "EUR",
  "favoriteTeams": ["Real Madrid"],
  "newsCategories": ["technology", "sports", "world"],
  "newsSources": ["El País", "El Mundo", "BBC Español"]
}
```

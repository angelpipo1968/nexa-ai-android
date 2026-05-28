# Smart Home Control — Módulo Hogar Inteligente

## Dispositivos Soportados

### 💡 Iluminación
| Comando | Acción |
|---------|--------|
| "Enciende la luz" | on: true |
| "Apaga la luz" | on: false |
| "Pon la luz al 50%" | brightness: 50 |
| "Luz azul" | color: "#0000FF" |
| "Modo noche" | brightness: 10, color: warm |
| "Modo cine" | brightness: 20, color: "#FF6600" |
| "Modo lectura" | brightness: 80, color: cool-white |
| "Modo concentración" | brightness: 100, color: daylight |
| "Luz romántica" | brightness: 30, color: "#FF3300" |

**Rooms soportadas:** salón, cocina, dormitorio, baño, oficina, entrada, garaje, jardín, terraza, habitación de invitados.

---

### 🌡️ Termostato / Climatización
| Comando | Acción |
|---------|--------|
| "Pon la calefacción a 22°" | setpoint: 22, mode: heat |
| "Enciende el aire" | mode: cool, auto-temp |
| "Modo eco" | mode: eco, setpoint: 19 |
| "Apaga el climatizador" | off |
| "¿A qué temperatura está la casa?" | report current temp |
| "Modo vacaciones" | setpoint: 17, schedule: off |
| "Modo turbo frío" | mode: cool, fan: max |
| "Programar calefacción a las 7" | schedule: 07:00, heat |

---

### 📺 Entretenimiento / AV
| Comando | Acción |
|---------|--------|
| "Enciende la tele" | tv: on |
| "Apaga la tele" | tv: off |
| "Canal 1 / Canal 5" | channel: N |
| "Sube / baja el volumen" | volume: +/- 10 |
| "Silencio" | mute: true |
| "Pon Netflix" | launch: Netflix |
| "Pon YouTube" | launch: YouTube |
| "Pon HBO / Disney+" | launch: HBO/Disney+ |
| "Pausa / Reproduce" | media: pause/play |
| "Atrás 30 segundos" | seek: -30s |

---

### 🔒 Seguridad y Acceso
| Comando | Acción |
|---------|--------|
| "Cierra la puerta" | lock: true |
| "Abre la puerta" | lock: false |
| "¿Está cerrada la puerta?" | lock: status |
| "Activa la alarma" | alarm: arm |
| "Desactiva la alarma" | alarm: disarm |
| "Modo ausente" | alarm: arm-away |
| "¿Quién está en la puerta?" | doorbell: camera-feed |
| "Activa cámaras" | cameras: on |

---

### 🏠 Electrodomésticos
| Comando | Acción |
|---------|--------|
| "Pon el robot aspirador" | roomba: start |
| "Para el robot" | roomba: stop |
| "Pon la lavadora" | washer: start |
| "¿Está lista la lavadora?" | washer: status |
| "Enciende el lavavajillas" | dishwasher: start |
| "Precalienta el horno a 180°" | oven: preheat, 180°C |
| "Enciende la cafetera" | coffee: brew |
| "Cafetera lista para las 7" | coffee: schedule 07:00 |

---

### 🌱 Jardín / Exterior
| Comando | Acción |
|---------|--------|
| "Riega el jardín" | irrigation: on |
| "Para el riego" | irrigation: off |
| "Riego automático" | irrigation: schedule |
| "Enciende las luces del jardín" | outdoor-lights: on |
| "Abre el garaje" | garage: open |
| "Cierra el garaje" | garage: close |
| "¿Está abierto el garaje?" | garage: status |

---

### 🔌 Enchufes Inteligentes
| Comando | Acción |
|---------|--------|
| "Enciende el enchufe del escritorio" | plug[escritorio]: on |
| "Apaga todos los enchufes" | all-plugs: off |
| "¿Cuánto consume el enchufe?" | plug: power-usage |

---

## Escenas Predefinidas

| Escena | Qué hace |
|--------|----------|
| "Modo Buenos Días" | Luces 60%, calefacción 21°, noticias, cafetera |
| "Modo Trabajo" | Luz fría 100%, do-not-disturb, música ambiente |
| "Modo Cine" | TV on, luces 10% naranja, silencio teléfono |
| "Modo Buenas Noches" | Luces off, alarm set, termostato 18°, door lock |
| "Modo Vacaciones" | Todo off, alarma armed-away, riego mínimo |
| "Modo Fiesta" | Luces de colores, música, ventilación máx |
| "Modo Romántico" | Luces rojo suave 20%, música, TV off |
| "Modo Emergencia" | Luces 100%, puertas bloqueadas, alarma |

---

## Manejo de Ambigüedad

Si el comando no especifica la sala:
> "¿En qué habitación quieres que encienda la luz?"

Si hay múltiples dispositivos del mismo tipo:
> "¿Qué televisor: el del salón o el del dormitorio?"

Si el dispositivo no responde:
> "Parece que [dispositivo] no responde. ¿Quieres que lo intente de nuevo?"

## Registro de Dispositivos

Almacenado en `~/alexa-skill/devices.json`:
```json
{
  "devices": [
    {
      "id": "light_salon",
      "name": "luz del salón",
      "type": "light",
      "room": "salón",
      "capabilities": ["on", "off", "brightness", "color"],
      "status": "on",
      "brightness": 80,
      "color": "#FFFFFF"
    }
  ]
}
```

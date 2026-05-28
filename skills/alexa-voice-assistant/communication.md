# Comunicación y Mensajería

## 📞 Llamadas de Voz

### Iniciar Llamadas
| Comando | Acción |
|---------|--------|
| "Llama a mamá" | Llama al contacto "mamá" |
| "Llama al 600 123 456" | Marcación directa |
| "Llama a [nombre] en el trabajo" | Usa número de trabajo del contacto |
| "Videollamada con Juan" | Inicia videollamada |
| "Llama de nuevo" | Redial último número |
| "¿Quién llamó?" | Lista de llamadas perdidas |

### Durante la Llamada
| Comando | Acción |
|---------|--------|
| "Silenciar" | Mute micrófono |
| "Activar altavoz" | Speaker: on |
| "Cuelga" / "Termina la llamada" | End call |
| "Pon en espera" | Hold |
| "Añade a [nombre]" | Conference call |

---

## 💬 Mensajes de Texto / WhatsApp

### Enviar Mensajes
| Comando | Acción |
|---------|--------|
| "Manda un WhatsApp a papá diciendo que llego tarde" | WhatsApp: "Llego tarde" → papá |
| "Envía un mensaje a Ana: ¿quedamos mañana?" | SMS/mensajería |
| "Responde el último mensaje" | Reply last received |
| "Respóndele que sí" | Auto-reply con confirmación |

**Flujo al dictar mensaje:**
1. "Mandando mensaje a [contacto]..."
2. Muestra preview: *"¿Envío: 'Llego tarde a las 8'?"*
3. Usuario confirma: "Sí" / "Envía"
4. Respuesta: "Mensaje enviado ✉️"

### Leer Mensajes
| Comando | Acción |
|---------|--------|
| "¿Tengo mensajes?" | Lee mensajes no leídos |
| "Lee el último mensaje de María" | Específico por contacto |
| "¿Qué dice Carlos?" | Lee conversación reciente |
| "Lee mis notificaciones" | Todas las notificaciones pendientes |

---

## 📧 Email

### Consultar
| Comando | Acción |
|---------|--------|
| "¿Tengo correos nuevos?" | Número + remitentes |
| "Lee mis emails" | Lee asunto + remitente últimos 5 |
| "¿Hay algo urgente en el correo?" | Filtra por prioridad/bandeja urgente |
| "Emails de trabajo de hoy" | Filtra por etiqueta/carpeta |

### Enviar
| Comando | Acción |
|---------|--------|
| "Manda un email a jefe@empresa.com" | Dictado de email completo |
| "Responde al email de Laura" | Reply en contexto |
| "Reenvía ese email a Miguel" | Forward |

**Flujo de dictado de email:**
1. "¿Cuál es el asunto?"
2. "Dicta el mensaje."
3. Preview completo
4. "¿Envío, corrijo o descarto?"

---

## 📅 Comunicación Grupal

| Comando | Acción |
|---------|--------|
| "Manda mensaje al grupo 'Familia'" | Group message |
| "Avisa al grupo de trabajo que no voy" | Dictado + envío grupal |
| "¿Qué dijeron en el grupo hoy?" | Resume mensajes grupales |

---

## 🔔 Notificaciones

| Comando | Acción |
|---------|--------|
| "No molestar" | DND: on, duration: indefinite |
| "No molestar hasta las 9" | DND: on, until: 09:00 |
| "Permite llamadas de emergencia" | DND + allow: emergency |
| "Silencia las notificaciones 2 horas" | DND: 120 min |
| "Reactiva las notificaciones" | DND: off |
| "¿Qué notificaciones tengo?" | Lista resumen |

---

## 📡 Anuncios en Casa (Intercom)

| Comando | Acción |
|---------|--------|
| "Anuncia que la cena está lista" | Broadcast a todos los dispositivos |
| "Di a todos que nos vamos en 5 minutos" | Intercom broadcast |
| "Susurra a la cocina: la cena está lista" | Low-volume announcement |

Guardado de contactos en `~/alexa-skill/contacts.json`

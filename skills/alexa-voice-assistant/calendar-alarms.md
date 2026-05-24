# Calendario, Alarmas y Recordatorios

## 📅 Calendario

### Consultar Eventos
| Comando | Respuesta |
|---------|-----------|
| "¿Qué tengo hoy?" | Lista eventos del día actual |
| "¿Qué tengo mañana?" | Lista eventos del día siguiente |
| "¿Qué tengo esta semana?" | Resumen semanal |
| "¿Qué pasa el viernes?" | Eventos del día específico |
| "¿Tengo algo a las 3?" | Busca evento en hora específica |
| "¿Cuándo es mi próxima reunión?" | Primer evento próximo |
| "¿Qué tengo en diciembre?" | Eventos del mes |
| "¿Estoy libre el martes?" | Verifica disponibilidad |

### Crear Eventos
| Comando | Acción |
|---------|--------|
| "Crea una reunión el jueves a las 10" | Evento básico |
| "Añade cita con el dentista el lunes a las 12:30" | Con título |
| "Agenda cumpleaños de Ana el 15 de junio" | Fecha específica |
| "Reserva sala de reuniones mañana de 3 a 5" | Con duración |
| "Repite esta reunión cada lunes" | Evento recurrente |
| "Bloquea toda la mañana del viernes" | Bloqueador genérico |

**Flujo de creación:**
1. Detecta: qué + cuándo + duración (si se da)
2. Confirma: "Creando evento: [título] el [día] a las [hora]. ¿Lo confirmo?"
3. Si falta dato esencial → pregunta solo ese dato
4. Confirma: "Evento guardado. ✅"

### Modificar / Eliminar
| Comando | Acción |
|---------|--------|
| "Cancela la reunión del martes" | Elimina evento |
| "Mueve la cita a las 4" | Cambia hora |
| "Cambia la reunión al miércoles" | Cambia día |
| "Añade a Miguel a la reunión" | Agrega invitado |
| "¿Hay conflicto con mis eventos?" | Detecta solapamientos |

---

## ⏰ Alarmas

### Crear Alarmas
| Comando | Acción |
|---------|--------|
| "Ponme una alarma a las 7" | Alarma simple |
| "Despiértame a las 6:30" | Alias de alarma |
| "Alarma a las 8 de la mañana" | AM explícito |
| "Alarma en 3 horas" | Relativa al momento actual |
| "Alarma de lunes a viernes a las 7" | Recurrente laboral |
| "Alarma solo el fin de semana a las 9" | Recurrente fin de semana |
| "Alarma cada día a las 8" | Diaria |
| "Segunda alarma a las 7:15" | Múltiples alarmas |

### Gestionar Alarmas
| Comando | Acción |
|---------|--------|
| "¿Qué alarmas tengo?" | Lista todas las alarmas activas |
| "Cancela la alarma de las 7" | Elimina alarma específica |
| "Cancela todas las alarmas" | Elimina todas |
| "Pospón la alarma 10 minutos" | Snooze manual |
| "Desactiva la alarma de los fines de semana" | Deshabilita sin borrar |

**Al sonar la alarma:**
- "Buenos días. Son las [hora]. [Temperatura exterior]. Tienes [N] eventos hoy."
- Si dice "Snooze" → pospone 9 minutos por defecto
- Si dice "Para" → cancela alarma activa

---

## ⏱️ Temporizadores

### Crear
| Comando | Acción |
|---------|--------|
| "Temporizador de 10 minutos" | Timer: 10:00 |
| "Temporizador de 1 hora y media" | Timer: 1:30:00 |
| "Pon un timer de 30 segundos" | Timer: 0:30 |
| "Temporizador para los espaguetis: 8 minutos" | Timer con nombre |
| "Segundo temporizador de 5 minutos" | Timer múltiple |

### Gestionar
| Comando | Acción |
|---------|--------|
| "¿Cuánto tiempo queda?" | Estado del temporizador |
| "Pausa el temporizador" | Pausar |
| "Reanuda el temporizador" | Reanudar |
| "Cancela el temporizador" | Detener |
| "¿Cuánto le queda al de los espaguetis?" | Por nombre |

**Al finalizar:** "⏰ [Nombre del temporizador] ha terminado."

---

## 🔔 Recordatorios

### Crear
| Comando | Acción |
|---------|--------|
| "Recuérdame tomar la medicación a las 8" | Recordatorio diario |
| "Recuérdame llamar a mamá mañana" | Fecha + hora (pregunta hora si falta) |
| "Recuérdame comprar leche cuando salga" | Basado en ubicación (si disponible) |
| "Recuérdame cada viernes enviar el informe" | Recurrente |
| "Recuérdame esto en 1 hora" | Relativo |
| "Recuérdame el cumple de Ana el 15 de junio" | Fecha específica |
| "Recuérdame regar las plantas cada 3 días" | Intervalo personalizado |

### Gestionar
| Comando | Acción |
|---------|--------|
| "¿Qué recordatorios tengo?" | Lista todos |
| "Cancela el recordatorio de la medicación" | Elimina por nombre |
| "¿Cuándo es el próximo recordatorio?" | Siguiente en la lista |

---

## 📊 Almacenamiento

`~/alexa-skill/calendar.json`:
```json
{
  "events": [
    {
      "id": "evt_001",
      "title": "Reunión de equipo",
      "date": "2026-05-27",
      "time": "10:00",
      "duration": 60,
      "recurrence": "weekly",
      "days": ["monday"],
      "attendees": [],
      "notes": ""
    }
  ],
  "alarms": [
    {
      "id": "alm_001",
      "time": "07:00",
      "label": "Levantarse",
      "recurrence": "weekdays",
      "active": true,
      "snooze": 9
    }
  ],
  "reminders": [
    {
      "id": "rem_001",
      "text": "Tomar medicación",
      "datetime": "2026-05-25T08:00:00",
      "recurrence": "daily",
      "active": true
    }
  ]
}
```

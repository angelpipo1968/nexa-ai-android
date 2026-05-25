# Rutinas, Automatizaciones y Compras

## 🌅 Rutinas Inteligentes

### Rutina de Buenos Días
**Trigger:** "Buenos días" / "Empieza el día" / "Alexa, buenos días"

**Secuencia automática:**
1. 🌡️ Clima del día + recomendación de ropa
2. 📅 Eventos del calendario de hoy
3. 🔔 Recordatorios activos del día
4. 📰 Top 3 noticias
5. 🚦 Estado del tráfico si hay trayecto habitual
6. 🎵 Música suave de despertar (opcional)
7. 💡 Subir luces gradualmente (si hay smart home)
8. ☕ Encender cafetera (si está configurada)

**Personalización:**
| Comando | Acción |
|---------|--------|
| "Configura mi rutina de mañana" | Asistente de configuración paso a paso |
| "Quita las noticias de mi rutina" | Elimina componente |
| "Añade el pronóstico deportivo a mi rutina" | Agrega componente |
| "¿Qué tiene mi rutina de mañana?" | Lista componentes actuales |

---

### Rutina de Buenas Noches
**Trigger:** "Buenas noches" / "Me voy a dormir" / "Modo noche"

**Secuencia automática:**
1. 🔒 Verificar que puertas estén cerradas
2. 💡 Apagar todas las luces (o bajar al mínimo)
3. 🌡️ Ajustar termostato a temperatura nocturna (18-19°C)
4. ⏰ Confirmar alarma del día siguiente
5. 📱 Activar modo no molestar en dispositivos
6. 🎵 Activar sonido ambiental o silencio
7. 📋 Resumen breve del día siguiente
8. 💬 Frase de buenas noches

---

### Rutina de Salida de Casa
**Trigger:** "Me voy" / "Salgo" / "Hasta luego"

**Secuencia automática:**
1. 💡 Apagar luces
2. 🔒 Verificar/cerrar puertas
3. 🌡️ Modo eco en termostato
4. 📱 Activar modo ausente
5. 🚦 Estado del tráfico al destino habitual
6. 🌤️ Clima para el trayecto
7. ❓ "¿Necesitas que te recuerde algo antes de salir?"

---

### Rutina de Llegada a Casa
**Trigger:** "Llegué" / "Estoy en casa" / "Ya estoy"

**Secuencia automática:**
1. 💡 Encender luces de entrada
2. 🌡️ Activar climatización confort
3. 🔔 Leer notificaciones perdidas del día
4. 📬 "Tienes X mensajes sin leer"
5. 🎵 Música bienvenida (si está configurada)
6. 📋 Recordatorios de la tarde

---

### Rutinas Personalizadas
| Comando | Acción |
|---------|--------|
| "Crea una rutina llamada [nombre]" | Asistente de creación |
| "Ejecuta la rutina [nombre]" | Lanza rutina guardada |
| "¿Qué rutinas tengo?" | Lista rutinas creadas |
| "Edita la rutina [nombre]" | Modifica componentes |
| "Elimina la rutina [nombre]" | Borra rutina |
| "Programa la rutina [nombre] cada día a las 9" | Automatiza ejecución |

---

## 🛒 Compras y Listas

### Lista de Compras
| Comando | Acción |
|---------|--------|
| "Añade leche a la lista de compras" | Agrega ítem |
| "Añade pan, huevos y tomates" | Múltiples ítems |
| "¿Qué tengo en la lista?" | Lee la lista completa |
| "Elimina el pan de la lista" | Quita ítem |
| "Tachado: leche" | Marca como comprado |
| "Vacía la lista" | Borra toda la lista |
| "Lista de compras de esta semana" | Lista semanal |
| "Genera lista para la receta de paella" | Lista automática desde receta |
| "¿Me falta algo para hacer pasta?" | Verifica ingredientes vs lista |
| "Comparte la lista con Ana" | Envía la lista por mensaje |
| "Organiza la lista por secciones" | Agrupa: lácteos, frutas, carnes... |

### Historial y Patrones
| Comando | Acción |
|---------|--------|
| "¿Qué compré la semana pasada?" | Historial de compras |
| "Añade los productos de siempre" | Lista habitual predefinida |
| "¿Cuándo compré aceite de oliva?" | Busca en historial |

---

### Pedidos Online
| Comando | Acción |
|---------|--------|
| "Pide [producto]" | Inicia pedido online |
| "¿Dónde está mi pedido?" | Estado de entrega |
| "¿Cuándo llega mi paquete?" | Fecha estimada de entrega |
| "Rastrea el paquete [número]" | Tracking por número |
| "Cancela el pedido" | Solicitud de cancelación |
| "Devuelve el último pedido" | Inicia devolución |
| "¿Qué he pedido este mes?" | Historial de pedidos |
| "Vuelve a pedir lo de la semana pasada" | Repite pedido |

---

## 🔄 Automatizaciones por Condición

### Triggers Automáticos
```
SI [condición] → ENTONCES [acción]
```

| Condición | Acción automática |
|-----------|------------------|
| Al llegar a casa | Encender luces + música |
| Al salir de casa | Apagar todo + modo ausente |
| Si llueve mañana | Recordar paraguas al salir |
| Si hay tráfico | Avisar 10 min antes de salir |
| Si batería <20% | Avisar por altavoz |
| A las 22:00 todos los días | Iniciar rutina de noche |
| Los lunes a las 7:00 | Recordar reunión semanal |
| Si temperatura <15°C | Sugerir encender calefacción |

### Crear Automatización
**Comando:** "Si [condición] entonces [acción]"

**Ejemplos:**
> "Si son más de las 11 de la noche y está encendida la luz del salón, apágala"
> "Si llego a casa los viernes, pon música de fiesta"
> "Si mañana hay lluvia, recuérdame llevar paraguas al salir"

---

## 📦 Inventario del Hogar

| Comando | Acción |
|---------|--------|
| "Me queda poco detergente" | Añade a lista de compras |
| "Acabé el café" | Añade urgente a lista |
| "¿Qué me queda de [producto]?" | Consulta inventario |
| "Registra que compré aceite" | Actualiza inventario |
| "Avísame cuando se acabe el papel de cocina" | Alerta de stock |

---

## 💰 Gastos y Presupuesto

| Comando | Acción |
|---------|--------|
| "Anoté 45 euros en el supermercado" | Registra gasto |
| "Gasté 120 euros en ropa" | Gasto por categoría |
| "¿Cuánto he gastado este mes?" | Resumen mensual |
| "¿Cuánto gasté en comida esta semana?" | Por categoría y período |
| "Presupuesto mensual: 1500 euros" | Define límite |
| "¿Cuánto me queda del presupuesto?" | Saldo disponible |
| "Alerta si gasto más de 200 en ocio" | Alerta de categoría |

---

## Almacenamiento

`~/alexa-skill/routines.json`:
```json
{
  "routines": [
    {
      "id": "good_morning",
      "name": "Buenos días",
      "trigger": ["buenos días", "empieza el día"],
      "steps": ["weather", "calendar", "news", "traffic"],
      "time": null,
      "active": true
    }
  ],
  "shopping_lists": {
    "current": [
      { "item": "leche", "quantity": "2 litros", "bought": false },
      { "item": "pan", "quantity": "1 barra", "bought": false }
    ],
    "history": []
  },
  "expenses": [
    { "date": "2026-05-24", "amount": 45.50, "category": "supermercado", "note": "Compra semanal" }
  ],
  "budget": { "monthly": 1500, "categories": {} }
}
```

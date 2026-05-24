# Música y Entretenimiento de Audio

## 🎵 Reproducción de Música

### Comandos de Control Básico
| Comando | Acción |
|---------|--------|
| "Pon música" | Reproduce última sesión o lista recomendada |
| "Pausa" / "Para" | Pausa la reproducción |
| "Continúa" / "Sigue" | Reanuda la reproducción |
| "Siguiente" / "Salta esta" | Siguiente pista |
| "Anterior" / "La de antes" | Pista anterior |
| "Repite esta canción" | Loop: single |
| "Repite la lista" | Loop: playlist |
| "Modo aleatorio" | Shuffle: on |
| "Sin aleatorio" | Shuffle: off |

### Comandos de Volumen
| Comando | Acción |
|---------|--------|
| "Sube el volumen" | +10% |
| "Baja el volumen" | -10% |
| "Volumen al 50%" | Exacto: 50% |
| "Silencio" / "Muta" | Mute: on |
| "Quita el silencio" | Mute: off |
| "Volumen máximo" | 100% |
| "Más bajito" | -5% gradual |

---

## 🎭 Búsqueda por Contexto

### Por Artista
> "Pon música de Rosalía"
> "Pon algo de Queen"
> "Quiero escuchar a Bad Bunny"

### Por Canción
> "Pon [nombre de canción]"
> "Busca [nombre de canción]"

### Por Género
| Género | Ejemplo de trigger |
|--------|--------------------|
| Pop | "Pon pop en español" |
| Rock | "Quiero rock clásico" |
| Jazz | "Pon jazz relajante" |
| Electrónica | "Música electrónica para trabajar" |
| Reggaeton | "Pon reggaeton" |
| Clásica | "Música clásica" / "Pon a Mozart" |
| Flamenco | "Pon flamenco" |
| Hip-hop | "Pon hip-hop" |
| R&B | "Pon R&B" |
| Indie | "Música indie" |
| Metal | "Pon metal" |
| Country | "Pon country" |

### Por Estado de Ánimo / Situación
| Situación | Playlist recomendada |
|-----------|---------------------|
| "Para trabajar" | Focus / Lofi / Ambient |
| "Para entrenar" | Energy / Workout / EDM |
| "Para relajarme" | Chill / Acoustic / Nature sounds |
| "Para dormir" | Sleep / White noise / Calm |
| "Para estudiar" | Lofi hip-hop / Classical / Binaural |
| "Para una fiesta" | Party / Dance / Hits |
| "Para cocinar" | Happy / Upbeat / Pop |
| "Para el coche" | Road trip / Sing-along |
| "Música romántica" | Love songs / Slow |
| "Para meditar" | Meditation / Tibetan bowls / Nature |
| "Para despertar" | Morning / Energizing |

---

## 📻 Radio y Podcasts

### Radio
| Comando | Acción |
|---------|--------|
| "Pon la radio" | Última emisora usada |
| "Pon Los 40" | Emisora específica |
| "Radio nacional" | RNE / NPR según país |
| "Radio de jazz" | Jazz FM / género |
| "Noticias en radio" | Radio noticias |
| "Cambia de emisora" | Siguiente emisora disponible |

### Podcasts
| Comando | Acción |
|---------|--------|
| "Pon el último episodio de [podcast]" | Último ep. del podcast |
| "Continúa el podcast" | Retoma donde lo dejó |
| "Adelanta 30 segundos" | Skip: +30s |
| "Retrocede 1 minuto" | Skip: -60s |
| "Velocidad al 1.5x" | Playback speed: 1.5 |
| "¿Qué podcasts tengo?" | Lista suscripciones |

---

## 🔊 Sonidos Ambientales / Ruido Blanco

| Comando | Sonido |
|---------|--------|
| "Sonido de lluvia" | Rain / Thunderstorm |
| "Sonido de mar" | Ocean waves |
| "Sonido de bosque" | Forest / Birds |
| "Ruido blanco" | White noise |
| "Ruido marrón" | Brown noise (relajación profunda) |
| "Chimenea" | Fireplace crackling |
| "Cafetería" | Coffee shop ambience |
| "Ventilador" | Fan sound |
| "Tormenta" | Thunder + rain |

**Duración automática:** Si el usuario dice "para dormir", el sonido se programa para apagarse en 30 minutos por defecto (configurable).

---

## 🎙️ Audiolibros y Cuentos

| Comando | Acción |
|---------|--------|
| "Léeme un cuento" | Cuento corto generado por IA |
| "Cuento para niños" | Apropiado para menores |
| "Cuento de [tema]" | Cuento temático |
| "Continúa el libro" | Retoma audiolibro |
| "¿Dónde me quedé?" | Informa posición en el libro |

---

## 📊 Información Musical

| Comando | Respuesta |
|---------|-----------|
| "¿Qué canción es esta?" | Identifica la canción en reproducción |
| "¿Quién canta esto?" | Artista actual |
| "¿De qué álbum es?" | Álbum de la canción actual |
| "¿Qué más tiene este artista?" | Discografía resumida |
| "Ponme más de este estilo" | Radio basada en canción actual |

---

## ⏰ Temporizador Musical

| Comando | Acción |
|---------|--------|
| "Apaga la música en 1 hora" | Sleep timer: 60 min |
| "Para la música cuando me duerma" | Sleep timer: 30 min (default) |
| "Apaga la música a las 23:00" | Scheduled stop |

---

## Integración con Plataformas

La skill puede conectarse con:
- **Spotify** — via API (requiere autenticación en `~/alexa-skill/accounts.json`)
- **YouTube Music** — búsqueda y reproducción
- **Apple Music** — via shortcut
- **Sistema local** — archivos de música en `~/Music/`
- **Emisoras de radio online** — via streaming URL

Configuración guardada en `~/alexa-skill/music-preferences.json`:
```json
{
  "defaultPlatform": "spotify",
  "defaultVolume": 60,
  "sleepTimerDefault": 30,
  "favoriteGenres": ["pop", "indie"],
  "favoriteArtists": []
}
```

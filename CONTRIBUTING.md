# Contribuir a NEXA PRO

¡Gracias por tu interés en contribuir! Aquí tienes las guías.

## Requisitos

- Android Studio Hedgehog (2023.1.1) o superior
- JDK 17
- Android SDK 35
- Dispositivo físico o emulador con API 26+

## Configuración del entorno

1. Fork el repo
2. Clone tu fork:
   ```bash
   git clone https://github.com/tu-usuario/nexa-ai-android.git
   ```
3. Abre en Android Studio
4. Sync Gradle
5. Configura tu API key en Vercel (ver README.md)

## Flujo de trabajo

1. Crea una rama desde `main`:
   ```bash
   git checkout -b feature/mi-feature
   ```
2. Haz tus cambios
3. Ejecuta los tests:
   ```bash
   ./gradlew test
   ```
4. Ejecuta lint:
   ```bash
   ./gradlew detekt
   ```
5. Commit con mensaje descriptivo:
   ```bash
   git commit -m "feat: descripción del cambio"
   ```
6. Push y crea un Pull Request

## Convenciones de commits

Usamos [Conventional Commits](https://www.conventionalcommits.org/):

| Prefijo | Uso |
|---------|-----|
| `feat:` | Nueva funcionalidad |
| `fix:` | Corrección de bug |
| `refactor:` | Reestructurar código sin cambiar comportamiento |
| `docs:` | Documentación |
| `test:` | Agregar o modificar tests |
| `chore:` | Mantenimiento (deps, CI, etc.) |
| `style:` | Formato, espacios, punto y coma |
| `perf:` | Mejora de rendimiento |

## Estructura del código

```
app/src/main/java/com/nexa/ai/
├── data/           # Repositorios, stores, modelos de datos
├── ui/             # Composables de Jetpack Compose
│   └── theme/      # Tema y colores
└── viewmodel/      # ViewModels y managers
```

## Reglas de código

- **Kotlin** — seguimos el [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Compose** — composables pequeños y reutilizables
- **ViewModel** — un ViewModel por pantalla, delegar a managers
- **Tests** — tests unitarios para lógica de negocio
- **No secrets** — nunca subir API keys, tokens o passwords

## Tests

```bash
# Tests unitarios
./gradlew test

# Tests de instrumentación
./gradlew connectedAndroidTest

# Reporte de cobertura
./gradlew jacocoTestReport
```

## Reportar bugs

Abre un issue con:
1. Descripción del bug
2. Pasos para reproducir
3. Comportamiento esperado
4. Screenshots (si aplica)
5. Versión de Android y modelo del dispositivo

## Preguntas

Abre un issue con el tag `question` o únete al Discord de la comunidad.

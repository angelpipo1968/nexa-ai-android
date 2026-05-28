# APK Firmada - Instrucciones de Firma

## Generar el Keystore de Release

Si necesitas crear tu propio keystore (primera vez o si lo perdiste), ejecuta:

```bash
keytool -genkeypair -v \
  -keystore android/nexa-release.keystore \
  -alias nexa \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepassword TU_PASSWORD \
  -keypass TU_PASSWORD
```

## Configurar las Contraseñas

Crea el archivo `android/keystore.properties` con:

```properties
storeFile=nexa-release.keystore
storePassword=TU_PASSWORD
keyAlias=nexa
keyPassword=TU_PASSWORD
```

Para el módulo nativo, las credenciales están en `gradle.properties`:

```properties
NEXA_KEYSTORE_PASSWORD=TU_PASSWORD
NEXA_KEY_ALIAS=nexa
NEXA_KEY_PASSWORD=TU_PASSWORD
```

## Compilar la APK Firmada

### Desde Android Studio:
1. **Build** → **Generate Signed Bundle / APK...**
2. Selecciona **APK**
3. El keystore ya está configurado automáticamente
4. Selecciona el build variant **release**
5. Click **Create**

### Desde la línea de comandos:
```bash
# Módulo Capacitor (android/)
cd android
./gradlew assembleRelease

# La APK firmada estará en:
# android/app/build/outputs/apk/release/app-release.apk
```

## Notas Importantes
- **NUNCA** subas el archivo `.keystore` a Git (ya está excluido en .gitignore)
- **NUNCA** subas `keystore.properties` a Git (ya está excluido en .gitignore)
- **GUARDA** una copia de seguridad del keystore en un lugar seguro
- Si pierdes el keystore, **NO** podrás actualizar la app en Google Play
- Las contraseñas en `gradle.properties` son para desarrollo local únicamente

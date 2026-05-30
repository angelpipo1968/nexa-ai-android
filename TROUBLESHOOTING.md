# 🛠️ Checklist de Diagnóstico y Problemas Comunes - NEXA PRO AI

Esta guía contiene los pasos prácticos para diagnosticar, depurar y resolver problemas en los diferentes componentes de la arquitectura híbrida de **Nexa OS** (Next.js, Hermes Express Server, WebSocket Lark Client y Dify).

---

## 1. 📡 Conexión Persistente y Webhook de Lark

### ⚠️ El Bot no responde en los chats
* **Verificación de Credenciales**: Asegúrate de que `LARK_APP_ID` y `LARK_APP_SECRET` en tu `.env.local` coincidan exactamente con tu aplicación en la consola de Lark.
* **Proceso Caído**: Verifica si tu cliente WebSocket o servidor local están corriendo en segundo plano:
  ```powershell
  # Para verificar si el proceso de Node del WebSocket está activo:
  Get-Process node
  ```
* **Permisos Insuficientes**: En la consola de Lark, ve a **Scopes** y asegúrate de tener activo el permiso `im:message` (enviar y recibir mensajes).
* **Falta de Publicación**: Si editaste scopes o eventos, recuerda crear un nuevo borrador (versión `1.0.x`) y presionar **Publish** (Publicar) en la consola de Lark para aplicar los cambios.

### ⚠️ Error de Conexión Persistente (WebSocket)
* **Modo Persistent Activo**: Asegúrate de que en la consola de Lark -> **Event & Callbacks**, la opción *Receive events through persistent connection* esté activada (**On**).
* **Versión de SDK obsoleta**: Si experimentas desconexiones frecuentes, valida que el paquete de Lark esté correctamente instalado:
  ```powershell
  npm install @larksuiteoapi/node-sdk@latest
  ```

---

## 2. 🤖 Backend Next.js (Puerto 3000)

### ⚠️ Error `Failed to fetch` o Rutas 404
* **Estructura de Carpetas**: Next.js 15 prioriza la carpeta raíz `app/` sobre `src/app/`. Si tus APIs no responden, asegúrate de que las rutas estén copiadas en la carpeta raíz `app/api/`.
* **Servidor Apagado**: Valida si el servidor de Next.js está corriendo:
  ```powershell
  # Debería mostrar un escucha en el puerto 3000
  netstat -ano | findstr 3000
  ```
* **Caché Corrupto**: Si Next.js actúa de forma extraña o no compila las rutas dinámicas, limpia la caché de compilación:
  ```powershell
  Remove-Item -Recurse -Force .next
  npm run dev
  ```

---

## 3. 🧠 Dify AI Backend & Fallbacks

### ⚠️ Mensaje: "Error: DIFY_API_KEY no está configurada"
* **Variable `.env.local`**: Valida que la variable `DIFY_API_KEY` tenga el formato `app-xxxx` en tu archivo local.
* **Consistencia del Nombre**: Asegúrate de que no haya espacios en blanco ni caracteres invisibles al final de la clave en el archivo `.env.local`.

### ⚠️ Dify responde pero no funciona el chatflow
* **Validación de Studio**: Abre la consola de Dify, entra a la aplicación **🤖 NEXA** y haz clic en *Preview/Chat* para comprobar que el workflow no tenga bloques bloqueados o errores de tokens del LLM (ej. Gemini/Groq sin créditos).

---

## 4. 🎙️ Servidor Hermes (Puerto 3001)

### ⚠️ Conflicto de puerto `EADDRINUSE: address already in use :::3001`
* Ocurre cuando un proceso viejo de Next.js o Hermes se quedó bloqueado reteniendo el puerto 3001.
* **Solución en Windows (PowerShell)**:
  ```powershell
  # 1. Encontrar el PID que usa el puerto 3001
  $pidUsingPort = (Get-NetTCPConnection -LocalPort 3001 -ErrorAction SilentlyContinue).OwningProcess
  
  # 2. Si existe, finalizar el proceso de forma forzada
  if ($pidUsingPort) {
      Stop-Process -Id $pidUsingPort -Force
      Write-Host "Puerto 3001 liberado con éxito."
  } else {
      Write-Host "El puerto 3001 ya está libre."
  }
  ```

### ⚠️ Ollama no conecta localmente
* Si no tienes Ollama corriendo localmente, el servidor Hermes fallará de inmediato al intentar conectar.
* **Solución**: Asegúrate de tener configurada la variable `DIFY_API_KEY` en tu `.env.local` para que el **Fallback Transparente** se active de inmediato y procese todas las peticiones a través de Dify.

---

## 5. 🐙 Git y Autenticación con GitHub

### ⚠️ Error: `Password authentication is not supported`
* GitHub ya no admite contraseñas tradicionales de texto plano para operaciones `push` o `pull`.
* **Solución Rápida con GitHub CLI**:
  ```powershell
  # 1. Iniciar sesión con tu cuenta de GitHub de forma segura
  gh auth login
  
  # 2. Subir tus cambios locales a GitHub
  git push origin main
  ```
* **Solución Alternativa (Token de Acceso Personal - PAT)**:
  * Genera un token en GitHub -> *Settings -> Developer Settings -> Personal Access Tokens*.
  * Cuando la terminal te pida contraseña, introduce tu token generado en lugar de tu clave habitual.

# 🚀 CONFIGURACIÓN COMPLETA DE NEXAS AI EN PC LOCAL

## 📋 SITUACIÓN ACTUAL
- ✅ PC local: 192.168.50.158
- ✅ Usuario: angel
- ✅ Red: misma red
- ❌ SSH: conexión fallida (necesita configuración)
- ❌ NEXAS AI: no instalado

## 🎯 OBJETIVO
Configurar NEXAS AI en PC local para modo manos libre con app Android.

## 🔧 SOLUCIÓN COMPLETA

### OPCIÓN 1: CONFIGURACIÓN MANUAL EN PC LOCAL (RECOMENDADA)

#### PASO 1: CONFIGURAR SSH EN PC LOCAL
En tu PC local (192.168.50.158), ejecuta estos comandos:

```bash
# 1. Habilitar SSH
sudo systemctl start ssh
sudo systemctl enable ssh

# 2. Configurar firewall
sudo ufw default allow outgoing
sudo ufw default deny incoming
sudo ufw allow ssh
sudo ufw allow 3001
sudo ufw allow 55770
sudo ufw enable

# 3. Verificar estado
sudo systemctl status ssh
sudo ufw status

# 4. Probar conexión desde Windows
# En Windows: ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.158
```

#### PASO 2: INSTALAR NEXAS AI EN PC LOCAL
```bash
# 1. Instalar Node.js
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# 2. Instalar PM2
sudo npm install -g pm2

# 3. Crear directorio NEXAS AI
mkdir -p ~/nexas-ai
cd ~/nexas-ai

# 4. Copiar archivos de NEXAS AI
# (Puedes copiar manualmente o usar git)
git clone https://github.com/your-repo/nexas-ai.git .

# 5. Instalar dependencias
npm install

# 6. Configurar servicio PM2
cat > ecosystem.config.js << 'EOF'
module.exports = {
  apps: [{
    name: 'nexas-ai',
    script: 'nexus-mobile-server.js',
    instances: 1,
    exec_mode: 'cluster',
    env: {
      NODE_ENV: 'production',
      PORT: 3001
    }
  }]
}
EOF

# 7. Iniciar servicio
pm2 start ecosystem.config.js
pm2 save
pm2 startup

# 8. Abrir puerto
sudo ufw allow 3001

# 9. Verificar servicio
pm2 status
pm2 logs
```

#### PASO 3: PROBAR SERVIDOR
```bash
# Probar servidor local
curl http://localhost:3001/nexas-mobile.html
```

### OPCIÓN 2: CONFIGURACIÓN DESDE WINDOWS (SI SSH FUNCIONA)

Si SSH funciona después de la configuración manual, ejecuta estos comandos desde Windows:

```cmd
# Configurar firewall
ssh angel@192.168.50.158 "sudo ufw allow ssh"
ssh angel@192.168.50.158 "sudo ufw allow 3001"
ssh angel@192.168.50.158 "sudo ufw enable"

# Instalar Node.js
ssh angel@192.168.50.158 "curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -"
ssh angel@192.168.50.158 "sudo apt-get install -y nodejs"

# Instalar PM2
ssh angel@192.168.50.158 "sudo npm install -g pm2"

# Crear directorio
ssh angel@192.168.50.158 "mkdir -p ~/nexas-ai && cd ~/nexas-ai"

# Copiar archivos (necesitarás copiar manualmente)
# scp -r ./nexas-ai angel@192.168.50.158:~/nexas-ai/

# Instalar dependencias
ssh angel@192.168.50.158 "cd ~/nexas-ai && npm install"

# Configurar servicio
ssh angel@192.168.50.158 "cd ~/nexas-ai && cat > ecosystem.config.js << 'EOF'
module.exports = {
  apps: [{
    name: 'nexas-ai',
    script: 'nexus-mobile-server.js',
    instances: 1,
    exec_mode: 'cluster',
    env: {
      NODE_ENV: 'production',
      PORT: 3001
    }
  }]
}
EOF"

# Iniciar servicio
ssh angel@192.168.50.158 "cd ~/nexas-ai && pm2 start ecosystem.config.js"
ssh angel@192.168.50.158 "cd ~/nexas-ai && pm2 save"
ssh angel@192.168.50.158 "cd ~/nexas-ai && pm2 startup"

# Probar servidor
ssh angel@192.168.50.158 "curl http://localhost:3001/nexas-mobile.html"
```

### OPCIÓN 3: CONFIGURACIÓN ANDROID APP

La app Android ya está configurada para usar tu PC local:

```kotlin
// En MainActivity.kt
val serverUrl = "http://192.168.50.158:3001/nexas-mobile.html"
```

### OPCIÓN 4: ACCESO DIRECTO SIN SERVIDOR

Si no puedes configurar el servidor, puedes usar la app Android directamente:

1. **Copiar archivos de NEXAS AI** a la PC local
2. **Ejecutar servidor localmente**:
```bash
cd ~/nexas-ai
node nexas-mobile-server.js
```
3. **Acceder desde Android**: `http://192.168.50.158:3001/nexas-mobile.html`

## 🎉 ¡LISTO PARA MODO MANOS LIBRE!

Una vez configurado, tendrás:
- ✅ PC local: 192.168.50.158
- ✅ NEXAS AI: servidor corriendo en puerto 3001
- ✅ App Android: configurada para usar servidor local
- ✅ Modo manos libre: completamente funcional

## 📡 URL FINAL
```
http://192.168.50.158:3001/nexas-mobile.html
```

## 🔧 GESTIÓN DEL SERVIDOR

```bash
# Ver estado del servicio
pm2 status

# Ver logs
pm2 logs

# Reiniciar servicio
pm2 restart nexas-ai

# Detener servicio
pm2 stop nexas-ai

# Eliminar servicio
pm2 delete nexas-ai
```

## 🚀 PRÓXIMOS PASOS

1. **Configura SSH en la PC local** (Paso 1)
2. **Instala NEXAS AI** (Paso 2)
3. **Prueba el servidor** (Paso 3)
4. **Abre la app Android**
5. **¡Modo manos libre listo!**

**¿Empezamos con la configuración?** 😊
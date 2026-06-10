# 🚀 CONFIGURACIÓN COMPLETA DE NEXAS AI CON SERVIDOR UBUNTU

## 📋 SITUACIÓN ACTUAL
- ✅ Servidor activo: 192.168.50.146
- ✅ Clave SSH configurada: 89:d1:88:0d:04:9b:05:9d:3f:3c:07:71:b0:f0:a1:49
- ❌ SSH no accesible (firewall bloqueado)
- ❌ NEXAS AI no instalado

## 🎯 OBJETIVO
Integrar la app Android con tu servidor Ubuntu para modo manos libre.

## 🔧 PASOS PARA CONFIGURACIÓN

### PASO 1: CONFIGURAR SERVIDOR UBUNTU MANUALMENTE

Necesitas acceder a tu servidor Ubuntu (192.168.50.146) y ejecutar estos comandos:

```bash
# 1. CONFIGURAR SSH
sudo systemctl start ssh
sudo systemctl enable ssh
sudo ufw allow 22

# 2. VERIFICAR SSH
sudo systemctl status ssh

# 3. CONFIGURAR FIREWALL
sudo ufw default allow outgoing
sudo ufw default deny incoming
sudo ufw allow 22    # SSH
sudo ufw allow 3001  # NEXAS AI
sudo ufw allow 55770 # Puerto personalizado
sudo ufw enable

# 4. CONFIGURAR SSH POR CLAVE
mkdir -p ~/.ssh
chmod 700 ~/.ssh
echo 'ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDC8ehuLuoo8ofrzgqPChrua67UcHgTQifWLq3DMjBedYNZdU7ylHFPo2S/QuYEXRGSlIKbbh/B/Z3/BjNFaV6U0Y3lfI0+Y30jzqGheCZywUl4e/xy5yHLdCDBnUml8VeaK5Nfwrly6sgAA3k+KIZFEnyGROPZ1lqObj3ZgagqgclWUtmNK7p507/LNelPftsMO/KIPGS5WQdOjkiRpVJDdde4QA7u5XAViBuH0cBPW/+b/G6jQzYmZaEW7OKPocH+DPeBTzpaZYUaPCRRwJFGuFaVNTGbPqgdiLbPyxHEVerKzchVN4/voAju5mYnylzbLIuVEcP9Ugfna2gxQ8PP angel' >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys

# 5. PROBAR SSH DESDE WINDOWS
# En Windows: ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.146
```

### PASO 2: INSTALAR NEXAS AI EN EL SERVIDOR

Una que SSH funcione, ejecuta:

```bash
# Instalar Node.js
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Instalar PM2
sudo npm install -g pm2

# Crear directorio NEXAS AI
mkdir -p ~/nexas-ai
cd ~/nexas-ai

# Copiar archivos de NEXAS AI al servidor
# ( Necesitarás copiar los archivos manualmente o usar git )

# Instalar dependencias
npm install

# Crear archivo de configuración PM2
cat > ecosystem.config.js << 'EOF'
module.exports = {
  apps: [{
    name: 'nexas-ai',
    script: 'nexas-mobile-server.js',
    instances: 1,
    exec_mode: 'cluster',
    env: {
      NODE_ENV: 'production',
      PORT: 3001
    }
  }]
}
EOF

# Iniciar servicio
pm2 start ecosystem.config.js
pm2 save
pm2 startup

# Abrir puerto
sudo ufw allow 3001
```

### PASO 3: CONFIGURAR APP ANDROID

La app ya está configurada para usar tu servidor:

```kotlin
// En MainActivity.kt
val serverUrl = "http://192.168.50.146:3001/nexas-mobile.html"
```

### PASO 4: PROBAR CONEXIÓN

1. **Probar servidor:**
   ```bash
   curl http://192.168.50.146:3001/nexas-mobile.html
   ```

2. **Desde Android:**
   - Abre la app
   - Deberías ver la interfaz de NEXAS AI
   - Modo manos libre funcional

## 🚀 ALTERNATIVA SI SSH NO FUNCIONA

Si no puedes configurar SSH, puedes:

1. **Copiar archivos manualmente** al servidor
2. **Configurar NEXAS AI directamente** en el servidor
3. **Usar la URL:** `http://192.168.50.146:3001/nexas-mobile.html`

## 📡 URL FINAL
```
http://192.168.50.146:3001/nexus-mobile.html
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
```

## 🎉 ¡LISTO!

Una vez configurado, tendrás:
- ✅ App Android funcional
- ✅ Modo manos libre integrado
- ✅ Servidor Ubuntu corriendo NEXAS AI
- ✅ Acceso remoto completo

**¿Empezamos con la configuración del servidor?** 😊
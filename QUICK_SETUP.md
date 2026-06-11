# 🚀 CONFIGURACIÓN RÁPIDA DE NEXAS AI EN PC LOCAL

## 📋 PASOS RÁPIDOS

### 1. EN TU PC LOCAL (192.168.50.158)
```bash
# Habilitar SSH
sudo systemctl start ssh
sudo systemctl enable ssh

# Configurar firewall
sudo ufw default allow outgoing
sudo ufw default deny incoming
sudo ufw allow ssh
sudo ufw allow 3001
sudo ufw allow 55770
sudo ufw enable

# Verificar estado
sudo systemctl status ssh
sudo ufw status
```

### 2. PROBAR CONEXIÓN DESDE WINDOWS
```cmd
ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.158
```
Si pide contraseña: Marlenis147@

### 3. INSTALAR NEXAS AI
```bash
# Instalar Node.js
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Instalar PM2
sudo npm install -g pm2

# Crear directorio
mkdir -p ~/nexas-ai
cd ~/nexas-ai

# Copiar archivos de NEXAS AI
git clone https://github.com/your-repo/nexas-ai.git .

# Instalar dependencias
npm install

# Configurar servicio
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

# Iniciar servicio
pm2 start ecosystem.config.js
pm2 save
pm2 startup

# Abrir puerto
sudo ufw allow 3001
```

### 4. PROBAR SERVIDOR
```bash
curl http://localhost:3001/nexas-mobile.html
```

### 5. URL FINAL
```
http://192.168.50.158:3001/nexas-mobile.html
```

## 🎤 ¡MODO MANOS LIBRE LISTO!

La app Android ya está configurada para usar esta URL.

## 🔧 GESTIÓN DEL SERVIDOR
```bash
# Ver estado
pm2 status

# Ver logs
pm2 logs

# Reiniciar
pm2 restart nexas-ai

# Detener
pm2 stop nexas-ai
```

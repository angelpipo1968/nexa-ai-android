#!/bin/bash

echo "🚀 Configurando servidor NEXAS AI en Ubuntu..."
echo.

# Crear script completo para el servidor
cat > /tmp/nexas_server_setup.sh << 'EOF'
#!/bin/bash

echo "🔧 Instalando NEXAS AI en servidor Ubuntu..."

# Actualizar sistema
sudo apt update && sudo apt upgrade -y

# Instalar Node.js y npm
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Instalar PM2 (gestor de procesos)
sudo npm install -g pm2

# Crear directorio para NEXAS AI
sudo mkdir -p /opt/nexas-ai
sudo chown -R $USER:$USER /opt/nexas-ai
cd /opt/nexas-ai

# Instalar NEXAS AI
npm install nexa-ai-server

# Crear archivo de configuración
cat > /opt/nexas-ai/config.json << 'CONFIG_EOF'
{
  "port": 3001,
  "host": "0.0.0.0",
  "ssl": false,
  "maxConnections": 100,
  "voiceEnabled": true,
  "language": "es"
}
CONFIG_EOF

# Crear servicio PM2
cat > /opt/nexas-ai/ecosystem.config.js << 'ECO_EOF'
module.exports = {
  apps: [{
    name: 'nexas-ai',
    script: 'node_modules/nexa-ai-server/bin/server.js',
    instances: 1,
    exec_mode: 'cluster',
    env: {
      NODE_ENV: 'production',
      PORT: 3001
    }
  }]
}
ECO_EOF

# Iniciar servicio
pm2 start /opt/nexas-ai/ecosystem.config.js
pm2 save
pm2 startup

# Habilitar firewall para el puerto
sudo ufw allow 3001

# Mostrar estado
echo "✅ NEXAS AI instalado y corriendo"
echo "📡 Puerto: 3001"
echo "🌐 Acceso: http://$(hostname -I | awk '{print $1}'):3001"
echo "📊 PM2 status: pm2 status"
echo "🛑 Detener: pm2 stop nexas-ai"
echo "🔄 Reiniciar: pm2 restart nexas-ai"
EOF

echo "📁 Script de instalación del servidor creado"
echo.

# Ejecutar el script en el servidor
echo "🔄 Instalando NEXAS AI en el servidor Ubuntu..."

# Intentar ejecutar con sshpass
if command -v sshpass &> /dev/null; then
    echo "🔍 Instalando NEXAS AI usando sshpass..."
    sshpass -p 'Marlenis147@' ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.158 'bash -s' < /tmp/nexas_server_setup.sh
    if [ $? -eq 0 ]; then
        echo "✅ NEXAS AI instalado exitosamente en el servidor!"
        echo.
        echo "📱 Ahora puedes acceder desde tu Android app:"
        echo "http://192.168.50.158:3001/nexas-mobile.html"
        echo.
        echo "🔧 Para gestionar el servidor:"
        echo "ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.158"
        echo "pm2 status"
        echo "pm2 logs"
    else
        echo "❌ Instalación fallida"
    fi
else
    echo "❌ sshpass no disponible"
    echo.
    echo "📋 Manualmente en el servidor Ubuntu:"
    echo "1. Instalar Node.js: curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash - && sudo apt-get install -y nodejs"
    echo "2. Instalar PM2: sudo npm install -g pm2"
    echo "3. Clonar NEXAS AI: git clone https://github.com/tu-repo/nexas-ai.git"
    echo "4. Instalar dependencias: cd nexas-ai && npm install"
    echo "5. Iniciar: pm2 start ecosystem.config.js"
    echo "6. Abrir puerto: sudo ufw allow 3001"
fi

echo.
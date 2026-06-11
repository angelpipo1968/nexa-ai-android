#!/bin/bash

echo "🔧 CONFIGURACIÓN MANUAL DE SSH EN SERVIDOR UBUNTU"
echo "=================================================="
echo.
echo "Sigue estos pasos en tu servidor Ubuntu (192.168.50.158):"
echo.
echo "📋 PASO 1: Habilitar SSH"
echo "----------------------------------------"
echo "sudo systemctl start ssh"
echo "sudo systemctl enable ssh"
echo.
echo "📋 PASO 2: Abrir firewall"
echo "----------------------------------------"
echo "sudo ufw allow 22"
echo.
echo "📋 PASO 3: Verificar estado"
echo "----------------------------------------"
echo "sudo systemctl status ssh"
echo.
echo "📋 PASO 4: Probar conexión desde Windows"
echo "----------------------------------------"
echo "ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.158"
echo.
echo "📋 PASO 5: Si pide contraseña, usa:"
echo "----------------------------------------"
echo "Marlenis147@"
echo.
echo "📋 PASO 6: Una que SSH funcione, instalar NEXAS AI"
echo "----------------------------------------"
echo "curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -"
echo "sudo apt-get install -y nodejs"
echo "sudo npm install -g pm2"
echo "mkdir -p ~/nexas-ai"
echo "cd ~/nexas-ai"
echo "git clone https://github.com/your-repo/nexas-ai.git"
echo "cd nexas-ai"
echo "npm install"
echo "pm2 start ecosystem.config.js"
echo "pm2 save"
echo "pm2 startup"
echo "sudo ufw allow 3001"
echo.
echo "📡 URL del servidor una vez instalado:"
echo "http://$(hostname -I | awk '{print $1}'):3001/nexas-mobile.html"
echo.
echo "🚀 ¡Listo para integrar con Android app!"
echo.
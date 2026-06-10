#!/bin/bash

echo "🔧 SCRIPT PARA INSTALACIÓN SSH EN SERVIDOR UBUNTU"
echo "==============================================="
echo.
echo "Ejecuta estos comandos en tu servidor Ubuntu:"
echo.
echo "📋 PASO 1: Instalar SSH (si no está instalado)"
echo "----------------------------------------"
echo "sudo apt update"
echo "sudo apt install openssh-server"
echo.
echo "📋 PASO 2: Iniciar y habilitar SSH"
echo "----------------------------------------"
echo "sudo systemctl start ssh"
echo "sudo systemctl enable ssh"
echo.
echo "📋 PASO 3: Abrir firewall"
echo "----------------------------------------"
echo "sudo ufw allow 22"
echo.
echo "📋 PASO 4: Verificar estado"
echo "----------------------------------------"
echo "sudo systemctl status ssh"
echo.
echo "📋 PASO 5: Probar conexión desde Windows"
echo "----------------------------------------"
echo "ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.158"
echo.
echo "📋 PASO 6: Si pide contraseña, usa:"
echo "----------------------------------------"
echo "Marlenis147@"
echo.
echo "📋 PASO 7: Una vez SSH funcione, instalar NEXAS AI"
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
echo "📡 URL final del servidor:"
echo "http://192.168.50.158:3001/nexas-mobile.html"
echo.
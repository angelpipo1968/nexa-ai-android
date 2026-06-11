#!/bin/bash

echo "🔧 CONFIGURACIÓN DEL SERVIDOR UBUNTU"
echo "==================================="
echo.
echo "El servidor está activo pero firewall bloquea todos los puertos."
echo.
echo "📋 PASOS PARA CONFIGURAR EL SERVIDOR:"
echo "==================================="
echo.
echo "1. ACCEDER AL SERVIDOR UBUNTU:"
echo "   - Por consola local en el servidor"
echo "   - Por Putty/Terminal SSH (si está disponible)"
echo "   - Por interfaz web/administrativa"
echo.
echo "2. EJECUTAR COMANDOS EN EL SERVIDOR:"
echo "   ==================================="
echo.
echo "   # Habilitar firewall básico"
echo "   sudo ufw default allow outgoing"
echo "   sudo ufw default deny incoming"
echo.
echo "   # Abrir puertos necesarios"
echo "   sudo ufw allow 22    # SSH"
echo "   sudo ufw allow 80    # HTTP"
echo "   sudo ufw allow 443   # HTTPS"
echo "   sudo ufw allow 3001  # NEXAS AI"
echo "   sudo ufw allow 55770 # Puerto personalizado"
echo.
echo "   # Habilitar firewall"
echo "   sudo ufw enable"
echo.
echo "   # Verificar estado"
echo "   sudo ufw status"
echo.
echo "3. ALTERNATIVA: SI NO TIENES ACCESO AL SERVIDOR:"
echo "   ==============================================="
echo.
echo "   # Si tienes acceso web/administrativo:"
echo "   # - Busca 'Firewall' o 'Security' en la interfaz web"
echo "   # - Agrega reglas para los puertos: 22, 80, 443, 3001, 55770"
echo.
echo "4. DESPUÉS DE CONFIGURAR FIREWALL:"
echo "   ==================================="
echo.
echo "   # Probar conexión SSH"
echo "   ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.146"
echo.
echo "   # Si pide contraseña:"
echo "   Marlenis147@"
echo.
echo "   # Instalar NEXAS AI"
echo "   curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -"
echo "   sudo apt-get install -y nodejs"
echo "   sudo npm install -g pm2"
echo "   mkdir -p ~/nexas-ai"
echo "   cd ~/nexas-ai"
echo "   # Aquí necesitarías copiar los archivos de NEXAS AI"
echo "   npm install"
echo "   pm2 start ecosystem.config.js"
echo "   pm2 save"
echo "   sudo ufw allow 3001"
echo.
echo "5. URL FINAL:"
echo "   ============"
echo "   http://192.168.50.146:3001/nexas-mobile.html"
echo.
echo "🚀 ¡Listo para integrar con la app Android!"
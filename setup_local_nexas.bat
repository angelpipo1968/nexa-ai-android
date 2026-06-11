@echo off
echo 🚀 CONFIGURACIÓN NEXAS AI EN PC LOCAL
echo =====================================
echo.
echo PC Local: 192.168.50.158
echo Usuario: angel
echo.
echo 📋 PASOS PARA CONFIGURAR:
echo =========================
echo.
echo 1. EN TU PC LOCAL (192.168.50.158):
echo    =================================
echo.
echo    # Habilitar SSH
echo    sudo systemctl start ssh
echo    sudo systemctl enable ssh
echo.
echo    # Configurar firewall
echo    sudo ufw default allow outgoing
echo    sudo ufw default deny incoming
echo    sudo ufw allow ssh
echo    sudo ufw allow 3001
echo    sudo ufw allow 55770
echo    sudo ufw enable
echo.
echo    # Verificar estado
echo    sudo systemctl status ssh
echo    sudo ufw status
echo.
echo 2. PROBAR CONEXIÓN DESDE WINDOWS:
echo    ===============================
echo.
echo    ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.158
echo.
echo    Si pide contraseña: Marlenis147@
echo.
echo 3. SI SSH FUNCIONA, INSTALAR NEXAS AI:
echo    ====================================
echo.
echo    # Instalar Node.js
echo    curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
echo    sudo apt-get install -y nodejs
echo.
echo    # Instalar PM2
echo    sudo npm install -g pm2
echo.
echo    # Crear directorio
echo    mkdir -p ~/nexas-ai
echo    cd ~/nexas-ai
echo.
echo    # Copiar archivos de NEXAS AI
echo    git clone https://github.com/your-repo/nexas-ai.git .
echo.
echo    # Instalar dependencias
echo    npm install
echo.
echo    # Configurar servicio
echo    cat > ecosystem.config.js ^<^< 'EOF'
echo    module.exports = {
echo      apps: [{
echo        name: 'nexas-ai',
echo        script: 'nexus-mobile-server.js',
echo        instances: 1,
echo        exec_mode: 'cluster',
echo        env: {
echo          NODE_ENV: 'production',
echo          PORT: 3001
echo        }
echo      }]
echo    }
echo    EOF
echo.
echo    # Iniciar servicio
echo    pm2 start ecosystem.config.js
echo    pm2 save
echo    pm2 startup
echo.
echo    # Abrir puerto
echo    sudo ufw allow 3001
echo.
echo 4. PROBAR SERVIDOR:
echo    =================
echo.
echo    curl http://localhost:3001/nexas-mobile.html
echo.
echo 5. URL FINAL:
echo    ============
echo    http://192.168.50.158:3001/nexas-mobile.html
echo.
echo 🎤 ¡Modo manos libre listo!
echo.
echo 📝 NOTAS:
echo ========
echo - La app Android ya está configurada para usar esta URL
echo - Si SSH no funciona, configura manualmente en la PC local
echo - Necesitas copiar los archivos de NEXAS AI al servidor
echo.
echo 🚀 ¡EMPEZAMOS!
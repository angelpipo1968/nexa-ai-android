@echo off
echo 🔧 Configuración SSH por clave para Windows
echo ==========================================
echo.

echo 📋 PASOS PARA CONFIGURAR SSH EN WINDOWS:
echo ======================================
echo.

echo 1. Verificar si OpenSSH está instalado:
ssh -V

if %errorlevel% neq 0 (
    echo ❌ OpenSSH no encontrado
    echo Descarga OpenSSH para Windows:
    echo https://github.com/PowerShell/Win32-OpenSSH/releases
    echo.
    echo O instala mediante PowerShell:
    echo Add-WindowsCapability -Online -Name OpenSSH.Client
    pause
    exit /b 1
)

echo ✅ OpenSSH encontrado
echo.

echo 2. Probar conexión SSH con clave:
ssh -oKexAlgorithms=curve25519-sha256 -oStrictHostKeyChecking=no angel@192.168.50.146

if %errorlevel% equ 0 (
    echo ✅ SSH funciona!
    echo.
    echo 3. Instalar NEXAS AI en el servidor:
    echo ====================================
    echo ssh angel@192.168.50.146 "curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -"
    echo ssh angel@192.168.50.146 "sudo apt-get install -y nodejs"
    echo ssh angel@192.168.50.146 "sudo npm install -g pm2"
    echo ssh angel@192.168.50.146 "mkdir -p ~/nexas-ai"
    echo ssh angel@192.168.50.146 "cd ~/nexas-ai"
    echo ssh angel@192.168.50.146 "npm install"
    echo ssh angel@192.168.50.146 "pm2 start ecosystem.config.js"
    echo ssh angel@192.168.50.146 "pm2 save"
    echo.
    echo 4. URL final:
    echo =============
    echo http://192.168.50.146:3001/nexas-mobile.html
) else (
    echo ❌ SSH falló
    echo.
    echo 💡 Posibles soluciones:
    echo 1. La clave no está configurada en el servidor
    echo 2. Firewall bloquea el puerto 22
    echo 3. Servidor no está accesible
    echo.
    echo Para configurar manualmente en el servidor:
    echo mkdir -p ~/.ssh
    echo chmod 700 ~/.ssh
    echo echo 'ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDC8ehuLuoo8ofrzgqPChrua67UcHgTQifWLq3DMjBedYNZdU7ylHFPo2S/QuYEXRGSlIKbbh/B/Z3/BjNFaV6U0Y3lfI0+Y30jzqGheCZywUl4e/xy5yHLdCDBnUml8VeaK5Nfwrly6sgAA3k+KIZFEnyGROPZ1lqObj3ZgagqgclWUtmNK7p507/LNelPftsMO/KIPGS5WQdOjkiRpVJDdde4QA7u5XAViBuH0cBPW/+b/G6jQzYmZaEW7OKPocH+DPeBTzpaZYUaPCRRwJFGuFaVNTGbPqgdiLbPyxHEVerKzchVN4/voAju5mYnylzbLIuVEcP9Ugfna2gxQ8PP angel' >> ~/.ssh/authorized_keys
    echo chmod 600 ~/.ssh/authorized_keys
)

echo.
pause

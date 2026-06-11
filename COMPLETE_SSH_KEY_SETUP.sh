#!/bin/bash

echo "🔧 CONFIGURACIÓN SSH POR CLAVE COMPLETA"
echo "======================================"
echo.
echo "Clave SSH pública proporcionada:"
echo "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDC8ehuLuoo8ofrzgqPChrua67UcHgTQifWLq3DMjBedYNZdU7ylHFPo2S/QuYEXRGSlIKbbh/B/Z3/BjNFaV6U0Y3lfI0+Y30jzqGheCZywUl4e/xy5yHLdCDBnUml8VeaK5Nfwrly6sgAA3k+KIZFEnyGROPZ1lqObj3ZgagqgclWUtmNK7p507/LNelPftsMO/KIPGS5WQdOjkiRpVJDdde4QA7u5XAViBuH0cBPW/+b/G6jQzYmZaEW7OKPocH+DPeBTzpaZYUaPCRRwJFGuFaVNTGbPqgdiLbPyxHEVerKzchVN4/voAju5mYnylzbLIuVEcP9Ugfna2gxQ8PP angel"
echo.
echo "📋 PASOS PARA CONFIGURAR SSH POR CLAVE:"
echo "====================================="
echo.
echo "1. EN EL SERVIDOR UBUNTU (192.168.50.146):"
echo "   ======================================="
echo.
echo "   # Acceder al servidor (por consola local o método existente)"
echo "   # Ejecutar estos comandos:"
echo.
echo "   mkdir -p ~/.ssh"
echo "   chmod 700 ~/.ssh"
echo.
echo "   # Agregar tu clave SSH al authorized_keys"
echo "   echo 'ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDC8ehuLuoo8ofrzgqPChrua67UcHgTQifWLq3DMjBedYNZdU7ylHFPo2S/QuYEXRGSlIKbbh/B/Z3/BjNFaV6U0Y3lfI0+Y30jzqGheCZywUl4e/xy5yHLdCDBnUml8VeaK5Nfwrly6sgAA3k+KIZFEnyGROPZ1lqObj3ZgagqgclWUtmNK7p507/LNelPftsMO/KIPGS5WQdOjkiRpVJDdde4QA7u5XAViBuH0cBPW/+b/G6jQzYmZaEW7OKPocH+DPeBTzpaZYUaPCRRwJFGuFaVNTGbPqgdiLbPyxHEVerKzchVN4/voAju5mYnylzbLIuVEcP9Ugfna2gxQ8PP angel' >> ~/.ssh/authorized_keys"
echo.
echo "   chmod 600 ~/.ssh/authorized_keys"
echo.
echo "   # Verificar fingerprint"
echo "   ssh-keygen -lf ~/.ssh/authorized_keys"
echo.
echo "   # Debería mostrar: 89:d1:88:0d:04:9b:05:9d:3f:3c:07:71:b0:f0:a1:49"
echo.
echo "2. CONFIGURAR FIREWALL EN EL SERVIDOR:"
echo "   ==================================="
echo.
echo "   sudo ufw default allow outgoing"
echo "   sudo ufw default deny incoming"
echo "   sudo ufw allow 22    # SSH"
echo "   sudo ufw allow 3001  # NEXAS AI"
echo "   sudo ufw allow 55770 # Puerto personalizado"
echo "   sudo ufw enable"
echo.
echo "3. PROBAR CONEXIÓN SSH DESDE WINDOWS:"
echo "   ==================================="
echo.
echo "   ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.146"
echo.
echo "   # Debería conectarse sin pedir contraseña"
echo.
echo "4. SI LA CONEXIÓN SSH FUNCIONA, INSTALAR NEXAS AI:"
echo "   ================================================="
echo.
echo "   # En el servidor:"
echo "   curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -"
echo "   sudo apt-get install -y nodejs"
echo "   sudo npm install -g pm2"
echo "   mkdir -p ~/nexas-ai"
echo "   cd ~/nexas-ai"
echo "   # Copiar archivos de NEXAS AI al servidor"
echo "   npm install"
echo "   pm2 start ecosystem.config.js"
echo "   pm2 save"
echo "   pm2 startup"
echo.
echo "5. URL FINAL:"
echo "   ============"
echo "   http://192.168.50.146:3001/nexas-mobile.html"
echo.
echo "🚀 ¡SSH por clave listo para usar!"
echo.
echo "📝 Nota: Si no puedes acceder al servidor directamente,"
echo "necesitarás copiar estos comandos y ejecutarlos manualmente"
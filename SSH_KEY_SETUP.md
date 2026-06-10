#!/bin/bash

echo "🔧 CONFIGURACIÓN SSH POR CLAVE"
echo "============================="
echo.
echo "Fingerprint de clave SSH: 89:d1:88:0d:04:9b:05:9d:3f:3c:07:71:b0:f0:a1:49"
echo "Usuario: angel"
echo "Servidor: 192.168.50.146"
echo.
echo "📋 PASOS PARA CONFIGURAR SSH POR CLAVE:"
echo "====================================="
echo.
echo "1. EN EL SERVIDOR UBUNTU (192.168.50.146):"
echo "   ======================================="
echo.
echo "   # Crear directorio .ssh si no existe"
echo "   mkdir -p ~/.ssh"
echo "   chmod 700 ~/.ssh"
echo.
echo "   # Crear o agregar la clave autorizada"
echo "   echo 'ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQ...' > ~/.ssh/authorized_keys"
echo "   # Reemplaza '...' con tu clave pública completa"
echo.
echo "   # Configurar permisos"
echo "   chmod 600 ~/.ssh/authorized_keys"
echo.
echo "   # Verificar fingerprint"
echo "   ssh-keygen -lf ~/.ssh/authorized_keys"
echo.
echo "   # Verificar que coincida con: 89:d1:88:0d:04:9b:05:9d:3f:3c:07:71:b0:f0:a1:49"
echo.
echo "2. EN TU WINDOWS:"
echo "   ==============="
echo.
echo "   # Generar par de claves si no tienes"
echo "   ssh-keygen -t rsa -b 4096 -C \"angel@ubuntu-server\""
echo.
echo "   # Copiar clave pública al servidor"
echo "   ssh-copy-id -i ~/.ssh/id_rsa.pub angel@192.168.50.146"
echo.
echo "3. PROBAR CONEXIÓN SSH:"
echo "   ==================="
echo.
echo "   # Conexión sin contraseña"
echo "   ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.146"
echo.
echo "4. SI YA TIENES LA CLAVE PÚBLICA:"
echo "   ==============================="
echo.
echo "   # Si ya tienes la clave pública, ejecuta en el servidor:"
echo "   echo 'TU_CLAVE_PÚBLICA_COMPLETA' >> ~/.ssh/authorized_keys"
echo.
echo "5. CONFIGURAR FIREWALL (después de SSH):"
echo "   ====================================="
echo.
echo "   sudo ufw allow 22"
echo "   sudo ufw allow 3001"
echo "   sudo ufw enable"
echo.
echo "6. URL FINAL:"
echo "   ============"
echo "   http://192.168.50.146:3001/nexas-mobile.html"
echo.
echo "🚀 ¡SSH por lista para usar!"
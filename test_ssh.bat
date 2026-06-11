#!/bin/bash

echo "🔧 Configurando SSH para conectar con Ubuntu server..."
echo.

# Verificar si SSH está instalado
if ! command -v ssh &> /dev/null; then
    echo "❌ SSH no encontrado. Instalando..."
    sudo apt update
    sudo apt install -y openssh-client openssh-server
    echo "✅ SSH instalado"
else
    echo "✅ SSH ya está instalado"
fi

echo.

# Verificar si podemos conectarnos
echo "🔍 Probando conexión SSH..."
ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.158 "echo 'Conexión SSH exitosa'"

if [ $? -eq 0 ]; then
    echo "✅ SSH funciona correctamente"
    echo.
    echo "🚀 Ahora puedes ejecutar comandos en el servidor:"
    echo "ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.158 'tu-comando'"
    echo.
else
    echo "❌ SSH falla. Posibles soluciones:"
    echo "1. Verifica que el servidor tenga SSH habilitado"
    echo "2. Verifica firewall en el servidor"
    echo "3. Verifica usuario/contraseña"
    echo "4. Intenta con clave SSH en lugar de contraseña"
    echo.
    echo "Para habilitar SSH en el servidor:"
    echo "sudo systemctl start ssh"
    echo "sudo systemctl enable ssh"
    echo "sudo ufw allow 22"
fi

echo.
#!/bin/bash

echo "🚀 Configurando servidor Ubuntu para SSH..."
echo.

# Crear script para ejecutar en el servidor
cat > /tmp/ubuntu_ssh_setup.sh << 'EOF'
#!/bin/bash

echo "🔧 Configurando SSH en servidor Ubuntu..."

# Habilitar servicio SSH
sudo systemctl start ssh
sudo systemctl enable ssh

# Abrir puerto 22 en firewall
sudo ufw allow 22

# Verificar estado de SSH
sudo systemctl status ssh

# Mostrar IP del servidor
echo "IP del servidor: $(hostname -I)"

echo "✅ SSH configurado exitosamente"
EOF

echo "📁 Script de configuración creado"
echo.

# Intentar ejecutar el script en el servidor usando diferentes métodos
echo "🔄 Intentando configurar SSH en el servidor..."

# Método 1: Usar sshpass si está disponible
if command -v sshpass &> /dev/null; then
    echo "🔍 Usando sshpass para ejecutar script..."
    sshpass -p 'Marlenis147@' ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.158 'bash -s' < /tmp/ubuntu_ssh_setup.sh
elif [ $? -eq 0 ]; then
    echo "✅ SSH configurado exitosamente con sshpass"
else
    echo "❌ sshpass no disponible"
    
    # Método 2: Usar expect si está disponible
    if command -v expect &> /dev/null; then
        echo "🔍 Usando expect para ejecutar script..."
        expect -c "
        spawn ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.158
        expect \"password:\"
        send \"Marlenis147@\r\"
        expect \"$\"
        send \"bash -s < /tmp/ubuntu_ssh_setup.sh\r\"
        expect \"$\"
        send \"exit\r\"
        expect eof
        "
        if [ $? -eq 0 ]; then
            echo "✅ SSH configurado exitosamente con expect"
        else
            echo "❌ Expect falló"
        fi
    else
        echo "❌ No se pudo configurar SSH automáticamente"
        echo.
        echo "📋 Manualmente en el servidor Ubuntu:"
        echo "1. Habilitar SSH: sudo systemctl start ssh"
        echo "2. Habilitar arranque: sudo systemctl enable ssh"
        echo "3. Abrir firewall: sudo ufw allow 22"
        echo "4. Verificar: sudo systemctl status ssh"
        echo.
        echo "🔗 Luego probar conexión:"
        echo "ssh -oKexAlgorithms=curve25519-sha256 angel@192.168.50.158"
    fi
fi

echo.
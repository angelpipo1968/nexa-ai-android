# 🔍 REVISIÓN COMPLETA DEL SERVIDOR UBUNTU

## 📋 SITUACIÓN ACTUAL
- ✅ IP: 192.168.50.158 (accesible por ping)
- ✅ Usuario: angel
- ❌ SSH: necesita configuración manual
- ❌ NEXAS AI: no instalado aún

## 🎯 OBJETIVO
Revisar el estado completo del servidor antes de instalar NEXAS AI.

## 🔧 COMANDOS PARA REVISAR EL SERVIDOR

### PASO 1: CONECTARSE AL SERVIDOR
```bash
# Accede al servidor por consola local o método existente
# Luego ejecuta estos comandos:
```

### PASO 2: REVISAR SISTEMA OPERATIVO
```bash
# Información del sistema
uname -a
lsb_release -a

# Uso de recursos
top -bn1 | head -10
free -h
df -h

# Tiempo del sistema
date
```

### PASO 3: REVISAR SERVICIOS Y SOFTWARE
```bash
# Servicios activos
systemctl list-units --type=service --state=running | head -15

# Node.js instalado
which node
node --version

# npm instalado
which npm
npm --version

# PM2 instalado
which pm2
pm2 --version

# Procesos Node.js
ps aux | grep node

# Directorios importantes
ls -la ~/
ls -la /var/www/
ls -la /opt/
```

### PASO 4: REVISAR RED Y PUERTOS
```bash
# Interfaces de red
ip addr show

# Puertos abiertos
sudo netstat -tlnp | head -20
sudo ss -tlnp | head -20

# Conectividad local
curl -s http://localhost:80 || echo "Puerto 80 cerrado"
curl -s http://localhost:3001 || echo "Puerto 3001 cerrado"
curl -s http://localhost:55770 || echo "Puerto 55770 cerrado"
```

### PASO 5: REVISAR FIREWALL
```bash
# Estado del firewall
sudo ufw status

# Reglas de firewall
sudo ufw verbose

# Si firewall no está activado:
sudo ufw enable
```

### PASO 6: REVISAR USUARIOS Y PERMISOS
```bash
# Usuario actual
whoami
groups

# Directorio home
echo $HOME
ls -la $HOME

# Permisos de sudo
sudo -l
```

## 📊 REPORTE DE REVISIÓN

### 🎯 ESTADO ESPERADO
- ✅ Sistema operativo: Ubuntu (versión reciente)
- ✅ CPU/Memoria: Uso normal (< 80% CPU, > 1GB RAM libre)
- ✅ Espacio en disco: > 5GB disponible
- ✅ Node.js: instalado (versión 18+)
- ✅ npm: instalado
- ✅ PM2: instalado
- ✅ Firewall: permitiendo puertos 22, 3001, 55770
- ✅ Servicios: SSH activo

### 🔧 RECOMENDACIONES

#### SI Node.js NO ESTÁ INSTALADO:
```bash
# Instalar Node.js 18
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Verificar instalación
node --version
npm --version
```

#### SI PM2 NO ESTÁ INSTALADO:
```bash
# Instalar PM2
sudo npm install -g pm2

# Verificar instalación
pm2 --version
```

#### SI FIREWALL NO ESTÁ CONFIGURADO:
```bash
# Configurar firewall básico
sudo ufw default allow outgoing
sudo ufw default deny incoming
sudo ufw allow 22    # SSH
sudo ufw allow 3001  # NEXAS AI
sudo ufw allow 55770 # Puerto personalizado
sudo ufw enable

# Verificar estado
sudo ufw status
```

#### SI HAY ESPACIO LIMITADO:
```bash
# Limpiar paquetes no utilizados
sudo apt autoremove
sudo apt clean

# Limpiar caché
sudo rm -rf /var/cache/apt/archives/*.deb
```

## 🚦 PRÓXIMOS PASOS DESPUÉS DE LA REVISIÓN

### 1. SI TODO ESTÁ CORRECTO:
```bash
# Instalar NEXAS AI
mkdir -p ~/nexas-ai
cd ~/nexas-ai
# Copiar archivos de NEXAS AI
npm install
pm2 start ecosystem.config.js
pm2 save
pm2 startup
```

### 2. SI HAY PROBLEMAS:
- Solucionar cada problema según las recomendaciones
- Volver a revisar
- Luego proceder con la instalación

### 3. VERIFICACIÓN FINAL:
```bash
# Probar servidor
curl http://localhost:3001/nexas-mobile.html

# Probar desde red
curl http://$(hostname -I | awk '{print $1}'):3001/nexas-mobile.html
```

## 🎯 URL FINAL
```
http://192.168.50.158:3001/nexas-mobile.html
```

## 📝 NOTAS IMPORTANTES
- El servidor debe tener acceso a internet para descargar Node.js y npm
- Se recomienda tener al menos 2GB de RAM libre
- El espacio en disco debe ser suficiente para los archivos de NEXAS AI
- El firewall debe permitir los puertos necesarios
- Los permisos de sudo son necesarios para instalar software de sistema

**¡Ejecuta estos comandos en tu servidor y avísame los resultados!** 😊
#!/bin/bash

# Script para compilar el proyecto

echo "======================================"
echo "Compilando Cliente RAFT"
echo "======================================"
echo ""

# Verificar Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ Error: Maven no está instalado"
    echo "Instala Maven: https://maven.apache.org/install.html"
    exit 1
fi

# Verificar Java
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java no está instalado"
    echo "Instala Java 17+: https://adoptium.net/"
    exit 1
fi

# Verificar versión de Java
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Error: Se requiere Java 17 o superior"
    echo "Versión actual: $JAVA_VERSION"
    exit 1
fi

echo "✓ Maven encontrado"
echo "✓ Java $JAVA_VERSION encontrado"
echo ""

# Limpiar y compilar
echo "Compilando proyecto..."
mvn clean package

if [ $? -eq 0 ]; then
    echo ""
    echo "======================================"
    echo "✓ Compilación exitosa"
    echo "======================================"
    echo ""
    echo "Archivos generados:"
    echo "  - target/raft-client-cli.jar (Cliente CLI)"
    echo "  - target/classes/ (Cliente Desktop)"
    echo ""
    echo "Para ejecutar:"
    echo "  CLI:     java -jar target/raft-client-cli.jar help"
    echo "  Desktop: mvn javafx:run"
    echo ""
else
    echo ""
    echo "❌ Error en la compilación"
    exit 1
fi

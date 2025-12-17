#!/bin/bash

# Script para ejecutar el Cliente Desktop (UI)

echo "Iniciando Cliente Desktop..."
echo ""

# Verificar que el proyecto esté compilado
if [ ! -d "target/classes" ]; then
    echo "El proyecto no está compilado. Compilando..."
    ./build.sh
    if [ $? -ne 0 ]; then
        echo "Error al compilar el proyecto"
        exit 1
    fi
fi

# Ejecutar la aplicación JavaFX
mvn javafx:run

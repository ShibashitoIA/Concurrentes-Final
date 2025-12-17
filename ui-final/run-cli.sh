#!/bin/bash

# Script para ejecutar el Cliente CLI

# Verificar que el JAR exista
if [ ! -f "target/raft-client-cli.jar" ]; then
    echo "El JAR no existe. Compilando..."
    ./build.sh
    if [ $? -ne 0 ]; then
        echo "Error al compilar el proyecto"
        exit 1
    fi
fi

# Ejecutar el cliente CLI con los argumentos proporcionados
java -jar target/raft-client-cli.jar "$@"

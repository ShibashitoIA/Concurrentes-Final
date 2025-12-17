#!/bin/bash
# Script de compilación para Main Worker (Linux/Mac)

echo "===================================="
echo "Compilando Main Worker"
echo "===================================="

# Crear directorio de salida
mkdir -p out

# Compilar raft-core primero
echo ""
echo "[1/2] Compilando raft-core..."
cd ../raft-core
mkdir -p out
javac -d out src/main/java/com/rafthq/core/*.java
if [ $? -ne 0 ]; then
    echo "ERROR: Fallo la compilación de raft-core"
    cd ../main-worker
    exit 1
fi
cd ../main-worker

# Compilar main-worker
echo ""
echo "[2/2] Compilando main-worker..."
javac -d out -cp ../raft-core/out src/main/java/com/mainworker/core/*.java
if [ $? -ne 0 ]; then
    echo "ERROR: Fallo la compilación de main-worker"
    exit 1
fi

echo ""
echo "===================================="
echo "Compilación exitosa!"
echo "===================================="
echo ""
echo "Archivos compilados en: out/"
echo ""
echo "Para ejecutar un nodo:"
echo "  ./run-node1.sh"
echo "  ./run-node2.sh"
echo "  ./run-node3.sh"
echo ""

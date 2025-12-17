#!/bin/bash
# Script para ejecutar Main Worker - Nodo 1 (Linux/Mac)

echo "===================================="
echo "Main Worker - Nodo 1"
echo "===================================="
echo "Puerto RAFT: 7001"
echo "Puerto HTTP Monitor: 8001"
echo "Monitor: http://localhost:8001"
echo "===================================="
echo ""

java -cp out:../raft-core/out com.mainworker.core.MainWorker --config config/worker-node1.properties

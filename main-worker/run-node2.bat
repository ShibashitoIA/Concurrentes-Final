@echo off
REM Script para ejecutar Main Worker - Nodo 2 (Windows)

echo ====================================
echo Main Worker - Nodo 2
echo ====================================
echo Puerto RAFT: 7002
echo Puerto HTTP Monitor: 8002
echo Monitor: http://localhost:8002
echo ====================================
echo.

java -cp out;..\raft-core\out;..\ModuloIA\target\classes com.mainworker.core.MainWorker --config config\worker-node2.properties

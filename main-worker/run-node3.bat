@echo off
REM Script para ejecutar Main Worker - Nodo 3 (Windows)

echo ====================================
echo Main Worker - Nodo 3
echo ====================================
echo Puerto RAFT: 7003
echo Puerto HTTP Monitor: 8003
echo Monitor: http://localhost:8003
echo ====================================
echo.

java -cp out;..\raft-core\out;..\ModuloIA\target\classes com.mainworker.core.MainWorker --config config\worker-node3.properties

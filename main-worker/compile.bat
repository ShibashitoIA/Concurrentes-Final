@echo off
REM Script de compilación para Main Worker (Windows)

echo ====================================
echo Compilando Main Worker
echo ====================================

REM Crear directorios de salida
if not exist "out" mkdir out
if not exist "lib" mkdir lib

REM Compilar ModuloIA
echo.
echo [1/3] Compilando ModuloIA...
cd ..\ModuloIA
if not exist "target\classes" mkdir target\classes
javac -d target\classes src\main\java\com\mycompany\moduloia\api\*.java src\main\java\com\mycompany\moduloia\data\*.java src\main\java\com\mycompany\moduloia\features\*.java src\main\java\com\mycompany\moduloia\mlp\*.java src\main\java\com\mycompany\moduloia\storage\*.java 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Fallo la compilacion de ModuloIA
    cd ..\main-worker
    exit /b 1
)
cd ..\main-worker

REM Compilar raft-core
echo.
echo [2/3] Compilando raft-core...
cd ..\raft-core
if not exist "out" mkdir out
javac -d out src\main\java\com\rafthq\core\*.java
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Fallo la compilacion de raft-core
    cd ..\main-worker
    exit /b 1
)
cd ..\main-worker

REM Compilar main-worker
echo.
echo [3/3] Compilando main-worker...
javac -d out -cp ..\raft-core\out;..\ModuloIA\target\classes src\main\java\com\mainworker\core\*.java
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Fallo la compilacion de main-worker
    exit /b 1
)

echo.
echo ====================================
echo Compilacion exitosa!
echo ====================================
echo.
echo Para ejecutar un nodo:
echo   run-node1.bat
echo   run-node2.bat
echo   run-node3.bat
echo.

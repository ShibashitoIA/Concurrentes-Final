@echo off
REM Script para compilar el proyecto en Windows

echo ======================================
echo Compilando Cliente RAFT
echo ======================================
echo.

REM Verificar Maven
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: Maven no esta instalado
    echo Instala Maven: https://maven.apache.org/install.html
    exit /b 1
)

REM Verificar Java
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: Java no esta instalado
    echo Instala Java 17+: https://adoptium.net/
    exit /b 1
)

echo Maven encontrado
echo Java encontrado
echo.

REM Limpiar y compilar
echo Compilando proyecto...
call mvn clean package

if %errorlevel% equ 0 (
    echo.
    echo ======================================
    echo Compilacion exitosa
    echo ======================================
    echo.
    echo Archivos generados:
    echo   - target\raft-client-cli.jar ^(Cliente CLI^)
    echo   - target\classes\ ^(Cliente Desktop^)
    echo.
    echo Para ejecutar:
    echo   CLI:     java -jar target\raft-client-cli.jar help
    echo   Desktop: mvn javafx:run
    echo.
) else (
    echo.
    echo Error en la compilacion
    exit /b 1
)

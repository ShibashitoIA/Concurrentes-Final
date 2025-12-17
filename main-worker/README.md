# Main Worker - Sistema Distribuido RAFT

Worker principal que participa en consenso RAFT, gestiona archivos y modelos replicados, y expone un monitor HTTP.

## Inicio Rápido

### 1. Compilar

```powershell
cd main-worker
.\compile.bat
```

### 2. Ejecutar Cluster (3 terminales)

Terminal 1:

```powershell
.\run-node1.bat
```

Terminal 2:

```powershell
.\run-node2.bat
```

Terminal 3:

```powershell
.\run-node3.bat
```

### 3. Verificar

Abre el navegador en:

- <http://localhost:8001/>
- <http://localhost:8002/>
- <http://localhost:8003/>

Normalmente el nodo 1 es el líder.

## Enviar Comandos

Los comandos se envían al líder (puerto 8001):

### Registrar modelo

```powershell
Invoke-WebRequest -Uri "http://localhost:8001/command" -Method POST -Body "REGISTER_MODEL|model-001|neural-network|0.95|1702345678000"
```

### Enviar archivo

Crear archivo de prueba:

```powershell
echo "Hola mundo" > archivo.txt
```

Enviar:

```powershell
.\send-file.ps1 -FilePath "archivo.txt"
```

O manualmente:

```powershell
$b = [IO.File]::ReadAllBytes("archivo.txt")
$m = [Security.Cryptography.MD5]::Create()
$h = [BitConverter]::ToString($m.ComputeHash($b)).Replace("-","").ToLower()
$x = [Convert]::ToBase64String($b)
$c = "STORE_FILE|archivo.txt|$h|$($b.Length)|$x"
Invoke-WebRequest -Uri "http://localhost:8001/command" -Method POST -Body $c
```

### Ver resultados

```powershell
# Ver modelos
Invoke-WebRequest -Uri "http://localhost:8001/models" | Select -ExpandProperty Content

# Ver archivos
Invoke-WebRequest -Uri "http://localhost:8001/files" | Select -ExpandProperty Content
```

## Endpoints HTTP

| URL | Descripción |
|-----|-------------|
| GET / | Monitor web |
| GET /status | Estado RAFT |
| GET /files | Archivos replicados |
| GET /models | Modelos registrados |
| GET /health | Health check |
| POST /command | Enviar comandos (solo al líder) |

## Estructura

```
main-worker/
├── src/                    Código fuente
├── config/                 Configuración de nodos
├── storage/                Se crea al ejecutar
│   └── worker-node1/
│       ├── data/          Archivos guardados
│       ├── models/        Modelos registrados
│       ├── raft_state.txt Estado RAFT
│       └── raft_log.txt   Log de comandos
├── compile.bat             Compilar
├── run-node1.bat          Ejecutar nodo 1
├── run-node2.bat          Ejecutar nodo 2
└── run-node3.bat          Ejecutar nodo 3
```

Los datos se guardan en `storage/worker-nodeN/`, no en la raíz.

## Comandos Soportados

**STORE_FILE** - Guardar archivo

```
STORE_FILE|fileName|checksumMD5|sizeBytes|dataBase64
```

**REGISTER_MODEL** - Registrar modelo

```
REGISTER_MODEL|modelId|modelType|accuracy|timestamp
```

**TRAIN_MODEL** - Entrenar modelo (requiere ModuloIA)

```
TRAIN_MODEL|modelId|inputType|datasetPath|hyperparamsBase64
```

**PREDICT** - Predicción

```
PREDICT|requestId|modelId|inputType|inputDataBase64
```

**DELETE_FILE** - Eliminar archivo

```
DELETE_FILE|fileName
```

**LIST_FILES** - Listar archivos

```
LIST_FILES|pattern
```

**NOP** - No operación (testing)

```
NOP
```

## Configuración

Archivo `worker-node1.properties`:

```properties
node.id=main-worker-1
node.host=127.0.0.1
node.port=7001
peers=127.0.0.1:7002,127.0.0.1:7003
election.timeout.min.ms=500
election.timeout.max.ms=1000
heartbeat.interval.ms=200
storage.dir=./storage/worker-node1
```

Puertos:

- RAFT: 7001, 7002, 7003
- HTTP Monitor: 8001, 8002, 8003 (puerto RAFT + 1000)

## Verificar Replicación

Enviar comando:

```powershell
Invoke-WebRequest -Uri "http://localhost:8001/command" -Method POST -Body "REGISTER_MODEL|test|neural-network|0.99|1702345678000"
```

Verificar en los 3 nodos:

```powershell
Invoke-WebRequest -Uri "http://localhost:8001/models" | Select -ExpandProperty Content
Invoke-WebRequest -Uri "http://localhost:8002/models" | Select -ExpandProperty Content
Invoke-WebRequest -Uri "http://localhost:8003/models" | Select -ExpandProperty Content
```

Los 3 deben tener el mismo modelo.

## Reiniciar desde Cero

```powershell
# Detener nodos (Ctrl+C)
# Eliminar storage
Remove-Item -Recurse -Force storage
# Ejecutar de nuevo
.\run-node1.bat
.\run-node2.bat
.\run-node3.bat
```

## Troubleshooting

**Puerto ocupado:**

```powershell
netstat -ano | findstr :7001
taskkill /PID <PID> /F
```

**Error "Not leader":**
Envía comandos al líder. Revisa en /status cuál es el líder.

**Archivos no se guardan:**
Verifica que el checksum MD5 sea válido y los datos estén en Base64. Usa el script send-file.ps1.

**Modelos sin carpetas físicas:**
Normal. El registro se guarda en model_registry.txt. Las carpetas se crean cuando se entrena con el AIService real.

## Integración con ModuloIA

El worker integra automáticamente el módulo de IA usando reflection. El sistema detecta si ModuloIA está en el classpath:

- Si está disponible: Usa el AIService real para entrenar y predecir
- Si no está disponible: Usa modo stub (desarrollo)

Los scripts de compilación y ejecución ya incluyen ModuloIA.

## Probar Entrenamiento Real

Crear dataset:
```powershell
echo "1,2,3,0" > dataset.csv
echo "4,5,6,1" >> dataset.csv
.\send-file.ps1 -FilePath "dataset.csv"
```

Entrenar modelo:
```powershell
$params = "3,1,10,0.01,2,false,5000,28,28,false"
$paramsB64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($params))
$cmd = "TRAIN_MODEL|mi-modelo|TABULAR|dataset.csv|$paramsB64"
Invoke-WebRequest -Uri "http://localhost:8001/command" -Method POST -Body $cmd
```

Ver resultado:
```powershell
Invoke-WebRequest -Uri "http://localhost:8001/models" | Select -ExpandProperty Content
```

# Secondary Worker (Go)

Worker en Go que actúa como nodo seguidor simplificado de Raft y ejecuta comandos de almacenamiento/registro de modelos.

## Arquitectura rápida
- RPC TCP: maneja `REQUEST_VOTE` y `APPEND_ENTRIES` compatibles con el protocolo Java (`tcp_server.go`).
- Raft básico: términos, votos, replicación y aplicación de entradas (sin temporizador de elección) (`raft.go`).
- Máquina de estados: comandos `STORE_FILE`, `REGISTER_MODEL`, `TRAIN`, `PREDICT`; escribe salidas simuladas en `data/go-worker-1/` (`state_machine.go`).
- Persistencia utilitaria: guardar término, voto y log en disco (no integrada en `main.go`) (`persistence.go`).
- Config por constantes: `Host=0.0.0.0`, `RaftPort=9003`, `HttpPort=8003`, `NodeID=go-worker-1` (`config.go`).

## Requisitos
- Go >= 1.20 (go.mod declara 1.25.4).
- Puertos libres: TCP 9003 y HTTP 8003.

## Prueba de comunicación rápida con raft-core (importante)
Ejecuta el ```nodo 1``` y ```nodo 2``` de ```raft-core``` de acuerdo a la documentación y luego el worker secundario o usa los siguientes comandos

```bash
# Ejecución del nodo 1 (dentro de "./raft-core")
java -cp out com.rafthq.core.Main --config config/sample-node1.properties

# Ejecución del nodo 2 (dentro de "./raft-core")
java -cp out com.rafthq.core.Main --config config/sample-node2.properties

# Ejecuta el worker secundario como un 3er nodo (dentro de "./secondary-worker")
go run .
```

Una vez que el líder haya sido elegido, escribe los siguientes comandos en la terminal del nodo lider (usualmente el nodo 1)

```bash
# Guarda un archivo de texto con un mensaje en la carpeta "data/go-worker-1" del worker secundario
STORE_FILE|archivo_test.txt|000|15|SGVsbG8gV29ybGQ=

# Guarda un archivo de texto con un modelo en la carpeta "data/go-worker-1" del worker secundario
REGISTER_MODEL|modelo_go|MLP|0.99|123456789
```

## Ejecucion del worker
En la carpeta `secondary-worker`:
```bash
go run .
```
- Expone TCP Raft en `0.0.0.0:9003`.
- Expone HTTP monitor:
  - `GET http://localhost:8003/status` (estado del nodo).
  - `GET http://localhost:8003/log` (log replicado).
- Datos y salidas se guardan en `data/go-worker-1/`.

## Prueba de carga opcional
Desde `secondary-worker/go_stress`:
```bash
cd go_stress
go run .
```
- Envia 1000 operaciones simuladas contra `127.0.0.1:9003` y genera `stress_results.csv` con latencias y tasa de exito.

## Notas
- Se asume liderazgo externo: este nodo no inicia elecciones.
- Los comandos aplicados crean archivos simulados; no validan contenido real.
- Si cambias host/puertos/ID, ajusta las constantes en `config.go` y recompila.

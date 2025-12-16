# Examples - Integración Raft Core

Ejemplos de código que integran `raft-core` en diferentes workers:
- **AIWorkerExample.java** - Worker de IA con entrenamiento de modelos
- **StorageWorkerExample.java** - Worker de almacenamiento con HTTP monitor
- **SimpleClient.java** - Cliente para enviar comandos

---

## Setup Rápido

### 1. Compilar

```bash
# Compilar raft-core
cd ../raft-core && mkdir -p out
javac -d out src/main/java/com/rafthq/core/*.java

# Compilar ejemplos
cd ../examples && mkdir -p out
javac -d out -cp ../raft-core/out *.java
```

### 2. Ejecutar Cluster (3 terminales)

**Terminal 1:**
```bash
java -cp out:../raft-core/out com.ia.AIWorkerExample --config ../config/ai-worker-node1.properties
```

**Terminal 2:**
```bash
java -cp out:../raft-core/out com.storage.StorageWorkerExample --config ../config/storage-worker-node2.properties --http-port 8082
```

**Terminal 3:**
```bash
java -cp out:../raft-core/out com.storage.StorageWorkerExample --config ../config/storage-worker-node3.properties --http-port 8083
```

Después de ~2 segundos, uno se convierte en **LEADER**.

### 3. Enviar Comandos

```bash
# Enviar TRAIN (buscar el puerto del líder en los logs)
java -cp out SimpleClient localhost 9001 "TRAIN|model_001|/data/training.csv|e30="

# Enviar STORE_FILE
java -cp out SimpleClient localhost 9001 "STORE_FILE|test.txt|5d41402abc4b2a76b9719d911017c592|11|dGVzdCBjb250ZW50"

# Si responde "NOT_LEADER", prueba con otro puerto (9002 o 9003)
```

### 4. Verificar Estado

```bash
# HTTP Monitor (Storage Workers)
curl http://localhost:8082/status
curl http://localhost:8082/files
```

---

## Comandos Esenciales

```bash
# Compilar todo
cd examples && javac -d out -cp ../raft-core/out *.java

# Ejecutar nodo AI
java -cp out:../raft-core/out com.ia.AIWorkerExample --config ../config/ai-worker-node1.properties

# Ejecutar nodo Storage
java -cp out:../raft-core/out com.storage.StorageWorkerExample --config ../config/storage-worker-node2.properties --http-port 8082

# Enviar comando
java -cp out SimpleClient localhost 9001 "TRAIN|model|/data/train.csv|e30="

# Monitorear
curl http://localhost:8082/status

# Limpiar
pkill -f AIWorkerExample; pkill -f StorageWorkerExample
rm -rf data/ models/ files/ out/
```

---

## Troubleshooting

**"Address already in use":**
```bash
pkill -f AIWorkerExample; pkill -f StorageWorkerExample
```

**"NOT_LEADER":** Envía el comando a otro puerto (el líder cambia tras elecciones).

**Reset completo:**
```bash
rm -rf data/ models/ files/
```

---

## Documentación Completa

- Protocolo de comandos: [`../docs/protocol.md`](../docs/protocol.md)
- Guía de integración: [`../docs/integration-guide.md`](../docs/integration-guide.md)

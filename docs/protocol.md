# Protocolo de Comandos - Raft Application Layer

Este documento define los comandos que se propagan a través del log de RAFT entre los nodos del cluster.

## Formato General

Los comandos son strings UTF-8 separados por `|`, serializados como `byte[]` para RAFT:

```
COMANDO|param1|param2|param3|...
```

---

## 1. Comandos de Entrenamiento de IA

### TRAIN_MODEL

Inicia el entrenamiento distribuido de un modelo de IA.

**Formato:**
```
TRAIN|<modelId>|<dataPath>|<hyperparamsBase64>
```

**Parámetros:**
- `modelId`: UUID único generado por el líder
- `dataPath`: Ruta al archivo de datos de entrenamiento (relativa o en storage)
- `hyperparamsBase64`: JSON codificado en Base64 con hiperparámetros

**Ejemplo:**
```
TRAIN|a3f5-8b2c-4d1e|/data/training_set_001.csv|eyJsZWFybmluZ1JhdGUiOiAwLjAxLCAiZXBvY2hzIjogMTAwfQ==
```

**Comportamiento en `onCommit`:**
- Todos los nodos AI-worker entrenan el modelo con los datos
- Guardan el modelo en `models/{modelId}.dat`
- Storage-workers replican el archivo de modelo

---

### PREDICT

Usa un modelo entrenado para hacer predicción.

**Formato:**
```
PREDICT|<modelId>|<inputBase64>|<requestId>
```

**Parámetros:**
- `modelId`: ID del modelo a usar
- `inputBase64`: Vector de entrada codificado en Base64
- `requestId`: ID para tracking de respuesta

**Ejemplo:**
```
PREDICT|a3f5-8b2c-4d1e|WzEuMiwgMy40LCA1LjZd|req_12345
```

**Comportamiento:**
- Solo el líder responde al cliente
- Followers ejecutan predict para mantener estado consistente (opcional)

---

## 2. Comandos de Gestión de Archivos

### STORE_FILE

Replica un archivo en todos los nodos del cluster.

**Formato:**
```
STORE_FILE|<fileName>|<checksumMD5>|<sizeBytes>|<chunkBase64>
```

**Parámetros:**
- `fileName`: Nombre del archivo (sin path)
- `checksumMD5`: Hash MD5 para verificación
- `sizeBytes`: Tamaño total en bytes
- `chunkBase64`: Contenido del archivo (o chunk) en Base64

**Ejemplo:**
```
STORE_FILE|training_data.csv|5d41402abc4b2a76b9719d911017c592|2048|ZGF0YTEsZGF0YTIsZGF0YTM=
```

**Comportamiento:**
- Todos los nodos guardan el archivo en `data/{fileName}`
- Verifican checksum antes de confirmar
- Si el archivo es grande, se divide en chunks múltiples

---

### DELETE_FILE

Elimina un archivo del cluster.

**Formato:**
```
DELETE_FILE|<fileName>
```

**Ejemplo:**
```
DELETE_FILE|old_model_v1.dat
```

---

### LIST_FILES

Solicita lista de archivos (query read-only, puede no requerir consenso).

**Formato:**
```
LIST_FILES|<pattern>
```

**Ejemplo:**
```
LIST_FILES|*.csv
```

---

## 3. Comandos de Gestión de Modelos

### REGISTER_MODEL

Registra metadata de un modelo entrenado.

**Formato:**
```
REGISTER_MODEL|<modelId>|<modelType>|<accuracy>|<timestampEpoch>
```

**Parámetros:**
- `modelId`: ID único del modelo
- `modelType`: Tipo (MLP, CNN, etc.)
- `accuracy`: Precisión alcanzada (0.0-1.0)
- `timestampEpoch`: Timestamp de creación

**Ejemplo:**
```
REGISTER_MODEL|a3f5-8b2c-4d1e|MLP|0.94|1702665600
```

---

### DELETE_MODEL

Elimina un modelo del registro y su archivo.

**Formato:**
```
DELETE_MODEL|<modelId>
```

---

## 4. Comandos de Control del Cluster

### NOP

No-operation, usado para testear replicación.

**Formato:**
```
NOP
```

---

### CHECKPOINT

Marca un punto de snapshot en el log.

**Formato:**
```
CHECKPOINT|<logIndex>
```

---

## 5. Convenciones de Respuesta (Cliente ↔ Líder)

Los comandos anteriores son **internos al log RAFT**. Las respuestas al cliente se envían por socket directo:

### Formato de respuesta

```
STATUS|<requestId>|<code>|<messageBase64>
```

**Códigos:**
- `200`: Éxito
- `400`: Error de validación
- `404`: Recurso no encontrado
- `500`: Error interno
- `503`: No soy líder (incluye `REDIRECT|<leaderHost>:<leaderPort>`)

**Ejemplo:**
```
STATUS|req_12345|200|eyJyZXN1bHQiOiBbMC45MSwgMC4wOV19
STATUS|req_99999|503|UkVESVJFQ1R8MTI3LjAuMC4xOjkwMDI=
```

---

## 6. Serialización

### Encoding/Decoding en Java

```java
// Crear comando
String cmd = String.join("|", "TRAIN", modelId, dataPath, paramsB64);
byte[] payload = cmd.getBytes(StandardCharsets.UTF_8);
node.appendCommand(payload);

// Parsear comando en onCommit
String cmdStr = new String(payload, StandardCharsets.UTF_8);
String[] parts = cmdStr.split("\\|", -1); // -1 para preservar campos vacíos
switch (parts[0]) {
    case "TRAIN":
        handleTrain(parts[1], parts[2], parts[3]);
        break;
    case "STORE_FILE":
        handleStoreFile(parts[1], parts[2], Integer.parseInt(parts[3]), parts[4]);
        break;
}
```

---

## 7. Ordenamiento y Consistencia

- **Todos los comandos** pasan por RAFT y se aplican en el mismo orden en todos los nodos.
- **Idempotencia**: Comandos deben ser idempotentes o incluir `requestId` para deduplicación.
- **Atomicidad**: Archivos grandes se dividen en chunks, el último chunk marca completitud.

---

## 8. Extensiones Futuras

- `TRAIN_DISTRIBUTED`: Para paralelismo de datos con agregación de gradientes
- `SNAPSHOT_CREATE`: Para compactación de log
- `CONFIG_CHANGE`: Para añadir/remover nodos dinámicamente


# raft-core (Java)

Núcleo RAFT y capa de red entre workers. Implementación en Java 11+ con sockets crudos y hilos.

## Objetivos
- Estados RAFT: Follower, Candidate, Leader.
- RPC: RequestVote, AppendEntries (heartbeats y replicación de log).
- API para la aplicación: `appendCommand(byte[] command)` y callback `onCommit(byte[] command)`.
- Concurrencia con hilos: recepción, heartbeats, timeouts, envíos a peers; sincronización del log y estado.
- Persistencia básica del log y estado (archivo append) y configuración por propiedades.

## Estructura inicial
- `config/` archivos `.properties` por nodo (IDs, host/puerto, peers, timeouts, rutas de log).
- `src/main/java/com/rafthq/core` código Java del núcleo RAFT.
- `scripts/` arranque múltiple (se agregará más adelante).

### Código actual (esqueleto)
- `NodeConfig`: carga de propiedades.
- `RaftState`, `RaftLog`, `RaftLogEntry`.
- `StateMachine`: callback `onCommit`.
- `RaftNode`: orquestación básica, timers stub y heartbeats vacíos.
- `RpcServer`, `RpcClient`: sockets bloqueantes con mensajes una línea.
- `MessageCodec`: serialización texto para RequestVote y AppendEntries (Base64 para payloads).

## Compilación rápida (sin build tool)
```
javac -d out $(find src/main/java -name "*.java")
```
Luego, ejecutar un nodo (ejemplo):
```
java -cp out com.rafthq.core.Main --config config/sample-node1.properties
```

## Testing local (3 nodos)
1. Crear configs para 3 nodos (sample-node1, node2, node3).
2. En terminales separadas:
   ```
   java -cp out com.rafthq.core.Main --config config/sample-node1.properties
   java -cp out com.rafthq.core.Main --config config/sample-node2.properties
   java -cp out com.rafthq.core.Main --config config/sample-node3.properties
   ```
3. Observar logs para elección (election timeout → CANDIDATE → LEADER).
4. Verificar que el LEADER envía heartbeats a followers cada 100ms (configurable).
5. Matar un nodo (Ctrl+C) y observar nueva elección en los supervivientes.

## Protocolo de Mensajes
Mensajes de texto delimitados por `\n`, campos separados por `|`. Payload binaria se codifica en Base64.

### RequestVote
```
REQUEST_VOTE|<term>|<candidateId>|<lastLogIndex>|<lastLogTerm>
REQUEST_VOTE_RESPONSE|<term>|<voteGranted>
```

### AppendEntries (heartbeat y replicación)
```
APPEND_ENTRIES|<term>|<leaderId>|<prevLogIndex>|<prevLogTerm>|<leaderCommit>|<entryCount>|[<entry1>|<entry2>|...]
APPEND_ENTRIES_RESPONSE|<term>|<success>|<matchIndex>
```
Cada entrada: `<index>,<term>,<length>,<payloadBase64>`

Ejemplo (3 nodos):
- Node1: `REQUEST_VOTE|1|node1|0|0` → Node2, Node3
- Node2, Node3 responden `REQUEST_VOTE_RESPONSE|1|true`
- Node1 se convierte en LEADER
- Node1 envía periódicamente `APPEND_ENTRIES|1|node1|0|0|0|0` (heartbeat)

## Implementado (Paso 1-2): Protocolo, estructuras y elección

### Clases de dato
- **`RaftLogEntry`**: índice, término, comando (bytes).
- **`RaftLog`**: almacén en memoria thread-safe de entradas. Métodos: `append()`, `get()`, `sliceFrom()`, `truncateFrom()`, `lastIndex()`, `lastTerm()`.
- **`NodeConfig`**: carga `.properties` (nodeId, host/port, peers, timeouts, storage dir).

### Mensajes RPC
- **`RequestVoteRequest/Response`**: term, candidateId, lastLogIndex/Term, voteGranted.
- **`AppendEntriesRequest/Response`**: term, leaderId, prevLogIndex/Term, leaderCommit, entries, success, matchIndex.
- **`MessageCodec`**: serializa/deserializa a texto (`|`-separado) con Base64 para payloads.

### Red
- **`RpcServer`**: servidor socket bloqueante, despacha mensajes a handler.
- **`RpcClient`**: cliente socket para enviar RequestVote/AppendEntries a peers.

### Orquestación RAFT
- **`RaftNode`** (completado):
  - **Estados**: FOLLOWER → election timeout → CANDIDATE → RequestVote → LEADER
  - **Timers**: elección con timeout aleatorio (min/max configurable), heartbeats periódicos en LEADER.
  - **Handlers**:
    - `handleRequestVote()`: valida term, comprueba log up-to-date, otorga voto.
    - `handleAppendEntries()`: valida prevLog, trunca conflictos, aplica commitIndex.
  - **Votación**: contar votos, alcanzar mayoría → LEADER.
  - **Heartbeats**: enviar AppendEntries vacíos periódicamente; followers responden con matchIndex.
  - **stepDown()**: si recibe term mayor, volver a FOLLOWER.
  - **API**: `appendCommand()` (solo LEADER), `getState()`, `getCurrentTerm()`.

- **`StateMachine`**: interfaz para callback `onCommit(byte[] cmd)` (aplicación de comandos comprometidos).

### Entrada
- **`Main.java`**: carga config desde `.properties`, inicia RaftNode, mantiene proceso vivo.

Compilación:
```
javac -d out $(find src/main/java -name "*.java")
```

## Próximos pasos (Paso 3-6)
1. **Replicación y commit**: Apply loop (`lastApplied` → `commitIndex`) que invoca `onCommit()`.
2. **Persistencia**: guardar/cargar currentTerm, votedFor, log a disco.
3. **Testing local**: 3 nodos en puertos distintos; verificar elección, heartbeats, replicación.
4. **Scripts de arranque**: batch/shell para iniciar cluster en LAN/WiFi.

## Configuración
Ver `config/sample-node1.properties` como referencia. Campos clave:
- `node.id` identificador único del nodo.
- `node.host`, `node.port` dirección local de escucha.
- `peers` lista de `host:port` de los demás nodos.
- `election.timeout.min.ms`, `election.timeout.max.ms`, `heartbeat.interval.ms`.
- `storage.dir` carpeta para log y estado.

## Próximos pasos
- Añadir clases base (`RaftNode`, `RaftLog`, `RpcServer`, `RpcClient`).
- Implementar timers y heartbeats.
- Conectar `appendCommand` con replicación y commit rule.
- Crear scripts de arranque múltiple y pruebas.

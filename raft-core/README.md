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
*(La clase Main se añadirá en el siguiente paso.)*

## Protocolo (borrador)
Mensajes de texto delimitados por `\n`, campos separados por `|`. Payload binaria se codifica en Base64.
- `REQUEST_VOTE|term|candidateId|lastLogIndex|lastLogTerm`
- `REQUEST_VOTE_RESPONSE|term|voteGranted`
- `APPEND_ENTRIES|term|leaderId|prevLogIndex|prevLogTerm|leaderCommit|entryCount|[base64Entry...]*`
  - Cada entrada: `index,term,length,payloadBase64` empaquetada en un campo; se concretará en el código.
- `APPEND_ENTRIES_RESPONSE|term|success|matchIndex`

## Plan de implementación
1) Modelo de datos RAFT: term, votedFor, log (index, term, payload), commitIndex/lastApplied, nextIndex/matchIndex.
2) Componentes concurrentes:
   - Listener de sockets que parsea RPCs.
   - Election timer por nodo; transición a Candidate y votación.
   - Heartbeats en Leader (AppendEntries vacíos).
   - Hilo aplicador que avanza commitIndex y dispara `onCommit`.
3) API pública:
   - `appendCommand(byte[] cmd)` solo en líder (followers responden redirección).
   - Registro de callback `onCommit`.
4) Persistencia mínima del log/estado a archivo y carga al arranque.
5) Scripts y pruebas locales con 3 nodos en puertos distintos.

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

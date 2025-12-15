# raft-core (Java)
Implementación Raft en Java 11+ con sockets TCP y hilos. Esta guía es para el equipo: mensajes, comandos y cómo se propagan.

## Qué hace
- Estados: Follower/Candidate/Leader con elección por timeouts aleatorios
- AppendEntries para replicar log y heartbeats
- Regla de commit por mayoría y apply ordenado vía `onCommit`
- API de app: `appendCommand(byte[] command)` (solo líder)
- Persistencia opcional: term, votedFor, log

## Estructura
```
raft-core/
├── config/                   # .properties por nodo
├── src/main/java/com/rafthq/core/
│   ├── RaftNode.java         # Lógica principal Raft
│   ├── RaftLog*.java         # Log y entradas
│   ├── RpcServer/Client.java # RPC TCP
│   ├── MessageCodec.java     # Serialización texto
│   ├── NodeConfig.java       # Configuración
│   ├── PersistentState.java  # Persistencia básica
│   └── Main.java             # Entrada
├── test_cluster.sh           # Arranque 3 nodos (bg)
├── test_e2e.sh               # Test end-to-end
└── out/                      # Binarios compilados
```

## Compilar
```bash
cd raft-core
mkdir -p out
javac -d out src/main/java/com/rafthq/core/*.java
```

## Ejecutar (3 terminales)
```bash
java -cp out com.rafthq.core.Main --config config/sample-node1.properties
java -cp out com.rafthq.core.Main --config config/sample-node2.properties
java -cp out com.rafthq.core.Main --config config/sample-node3.properties
```
Cuando veas "became LEADER", escribe líneas en la terminal del líder; se replican y aplican en todos como "Applied: ...".

## Mensajes RPC (texto sobre TCP)
- RequestVote: `REQUEST_VOTE|term|candidateId|lastLogIndex|lastLogTerm`
- VoteResponse: `VOTE_RESPONSE|term|voteGranted`
- AppendEntries: `APPEND_ENTRIES|term|leaderId|prevLogIndex|prevLogTerm|leaderCommit|entryCount|<entry1>|...`
- AppendResponse: `APPEND_RESPONSE|term|success|matchIndex`
- Entrada: `index,term,payloadBase64`

## Convención de comandos (payload)
- Un **command** es `byte[]` opaco para Raft. La app (state machine) decide el formato.
- En esta demo, cada línea ingresada por stdin del líder se envía como bytes UTF-8 y se aplica con `onCommit` en todos los nodos.
- Para integrar con otros módulos, define un esquema claro (ej. `OP|arg1|arg2|...` en texto) y pásalo como bytes al `appendCommand` del líder. Los followers lo aplican idéntico.

## Propagación de operaciones de cliente
1) Cliente habla con el **líder** y llama `appendCommand(payload)`
2) Líder agrega entrada al log local y envía AppendEntries a los peers
3) Cada follower valida `prevLogIndex/Term`, anexa entradas y responde `success`
4) Líder actualiza `matchIndex/nextIndex`, calcula mayoría y avanza `commitIndex`
5) Todos los nodos (incluido el líder) aplican en orden en el apply loop: `onCommit(payload)`

## Configuración mínima (sample-node1)
```properties
node.id=node1
node.host=127.0.0.1
node.port=9001
node.peers=127.0.0.1:9002,127.0.0.1:9003
# Timeouts (ms)
election.timeout.min=500
election.timeout.max=1000
heartbeat.interval=200
storage.dir=./storage/node1
```
Regla práctica: `heartbeat.interval` debe ser mucho menor que `election.timeout.min` para evitar elecciones innecesarias.

## Troubleshooting breve
- Sin líder / split votes: sube `election.timeout.max`
- BindException: mata procesos previos `pkill -f com.rafthq.core.Main`
- Connection refused: peers mal configurados o nodo caído

## Tests rápidos
- `./test_cluster.sh` arranca 3 nodos en background y muestra logs en `/tmp/node*.log`
- `./test_e2e.sh` elige líder, muestra estado y pasos para enviar comandos

## Licencia
Proyecto educativo para el curso de Sistemas Concurrentes 2025-2.# raft-core (Java)

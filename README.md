# Concurrentes-Final: Raft Consensus Implementation

Implementación educativa del algoritmo de consenso Raft en Java con sockets TCP y threading nativo.

## Características

✅ Elección de líder con timeouts aleatorios  
✅ Replicación de log con AppendEntries RPC  
✅ Commit/Apply de entradas comprometidas  
✅ Protocolo de mensajes text-based sobre TCP  
✅ Persistencia básica (term, votedFor, log)

## Quick Start

### Compilar

```bash
cd raft-core
mkdir -p out
javac -d out src/main/java/com/rafthq/core/*.java
```

### Ejecutar Cluster (3 terminales)

```bash
# Terminal 1
java -cp out com.rafthq.core.Main --config config/sample-node1.properties

# Terminal 2
java -cp out com.rafthq.core.Main --config config/sample-node2.properties

# Terminal 3
java -cp out com.rafthq.core.Main --config config/sample-node3.properties
```

Después de 1-2 segundos, uno será **LEADER**. Escribe comandos en la terminal del líder para replicar.

### Test Automático

```bash
cd raft-core
./test_e2e.sh
```

## Protocolo

**RequestVote:** `REQUEST_VOTE|term|candidateId|lastLogIndex|lastLogTerm`  
**AppendEntries:** `APPEND_ENTRIES|term|leaderId|prevLogIndex|prevLogTerm|leaderCommit|numEntries|entries...`

Ver [raft-core/README.md](raft-core/README.md) para documentación completa.

## Referencias

- [Raft Paper](https://raft.github.io/raft.pdf) - Diego Ongaro, 2014
- [Raft Visualization](https://raft.github.io/)

---
Proyecto educativo - Sistemas Concurrentes 2025-2
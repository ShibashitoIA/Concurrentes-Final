# Guía de Integración - Raft Core

Esta guía explica cómo integrar el núcleo RAFT (raft-core) en los diferentes módulos del proyecto.

## Arquitectura de Integración

```
┌─────────────────────────────────────────────────────────┐
│                    Desktop Client                        │
│              (Persona 4: GUI + Stress Test)              │
└────────────────────┬────────────────────────────────────┘
                     │ TCP Sockets
                     ↓
┌─────────────────────────────────────────────────────────┐
│                   Worker Nodes                           │
│  ┌────────────────────────────────────────────────────┐ │
│  │          RaftNode (raft-core)                      │ │
│  │  ┌──────────┐  ┌──────────┐  ┌─────────────┐     │ │
│  │  │ Election │  │ AppendEnt│  │ Apply Loop  │     │ │
│  │  │  Timer   │  │  RPCs    │  │ (onCommit)  │     │ │
│  │  └──────────┘  └──────────┘  └──────┬──────┘     │ │
│  └────────────────────────────────────────┼──────────┘ │
│                                           ↓              │
│  ┌────────────────────────────────────────────────────┐ │
│  │         Application State Machine                   │ │
│  │  • AI Worker (Persona 2): Training + Predict       │ │
│  │  • Storage Worker (Persona 3): File Replication   │ │
│  └────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

## 1. Dependencias

### Agregar raft-core al classpath

**Opción A: Referencia directa (desarrollo)**
```bash
javac -cp ../raft-core/out -d out src/**/*.java
java -cp ../raft-core/out:out com.myapp.Main
```

**Opción B: Crear JAR (producción)**
```bash
cd raft-core
jar cvf raft-core.jar -C out .
# Usar: java -cp raft-core.jar:myapp.jar com.myapp.Main
```

**Opción C: Maven/Gradle (recomendado para equipos grandes)**
```xml
<dependency>
    <groupId>com.rafthq</groupId>
    <artifactId>raft-core</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>compile</scope>
</dependency>
```

---

## 2. Configuración de Nodo

Cada worker necesita un archivo de configuración `.properties`:

### Ejemplo: `config/ai-worker-node1.properties`
```properties
node.id=ai-worker-1
node.host=192.168.1.10
node.port=9001
node.peers=192.168.1.11:9002,192.168.1.12:9003
election.timeout.min=500
election.timeout.max=1000
heartbeat.interval=200
storage.dir=./data/ai-worker-1
```

### Cargar configuración
```java
import com.rafthq.core.NodeConfig;

NodeConfig config = NodeConfig.fromFile("config/ai-worker-node1.properties");
```

---

## 3. Implementar State Machine

La interfaz `StateMachine` define cómo tu aplicación responde a comandos comprometidos:

```java
import com.rafthq.core.StateMachine;

StateMachine stateMachine = (byte[] command) -> {
    String cmdStr = new String(command, StandardCharsets.UTF_8);
    String[] parts = cmdStr.split("\\|", -1);
    
    switch (parts[0]) {
        case "TRAIN":
            handleTrain(parts[1], parts[2], parts[3]);
            break;
        case "PREDICT":
            handlePredict(parts[1], parts[2], parts[3]);
            break;
        case "STORE_FILE":
            handleStoreFile(parts[1], parts[2], Integer.parseInt(parts[3]), parts[4]);
            break;
        default:
            LOG.warning("Unknown command: " + parts[0]);
    }
};
```

**Importante:**
- `onCommit` se ejecuta **en todos los nodos** en el mismo orden.
- Debe ser **determinista** (sin rand(), timestamps, etc.).
- Maneja excepciones internamente (no dejes que rompan el apply loop).

---

## 4. Inicializar RaftNode

```java
import com.rafthq.core.RaftNode;
import com.rafthq.core.NodeConfig;
import java.io.IOException;

public class AIWorker {
    private RaftNode raftNode;
    
    public AIWorker(String configPath) throws IOException {
        NodeConfig config = NodeConfig.fromFile(configPath);
        
        StateMachine stateMachine = (byte[] cmd) -> {
            // Tu lógica de aplicación
        };
        
        this.raftNode = new RaftNode(config, stateMachine);
    }
    
    public void start() {
        raftNode.start();
        LOG.info("AIWorker started");
    }
}
```

---

## 5. Proponer Comandos (Solo Líder)

```java
public String trainModel(String dataPath, String hyperparams) {
    String modelId = UUID.randomUUID().toString();
    String cmd = String.join("|", "TRAIN", modelId, dataPath, hyperparams);
    
    boolean accepted = raftNode.appendCommand(cmd.getBytes(StandardCharsets.UTF_8));
    
    if (!accepted) {
        return "ERROR: Not leader";
    }
    
    return modelId; // Comando aceptado, se replicará
}
```

**Flujo:**
1. Cliente envía request al worker.
2. Worker verifica si es líder → `appendCommand()`.
3. Si no es líder → retornar `NOT_LEADER` o redirigir al líder.
4. Cuando el comando se commitea → `onCommit()` se ejecuta en todos.

---

## 6. Verificar Estado del Nodo

```java
import com.rafthq.core.RaftState;

public boolean isLeader() {
    return raftNode.getState() == RaftState.LEADER;
}

public int getCurrentTerm() {
    return raftNode.getCurrentTerm();
}

public int getCommitIndex() {
    return raftNode.getCommitIndex();
}
```

---

## 7. Ejemplo Completo: AI Worker

Ver [`examples/AIWorkerExample.java`](../examples/AIWorkerExample.java) para un ejemplo completo con:
- Servidor TCP para recibir requests de clientes
- Lógica de entrenamiento distribuido
- Manejo de redirects al líder
- Persistencia de modelos

---

## 8. Ejemplo Completo: Storage Worker

Ver [`examples/StorageWorkerExample.java`](../examples/StorageWorkerExample.java) para:
- Replicación de archivos via RAFT
- Servidor HTTP de monitoreo
- Verificación de checksums
- Listado de archivos replicados

---

## 9. Troubleshooting de Integración

### "No soy líder" en todos los nodos
- **Causa:** Ningún nodo puede contactar mayoría de peers.
- **Solución:** Verifica `node.peers` en configs, firewall, puertos.

### `onCommit` no se ejecuta
- **Causa:** Comando no alcanzó mayoría para commit.
- **Solución:** Verifica logs de replicación, asegura ≥2 nodos corriendo.

### Logs divergen entre nodos
- **Causa:** Bug en lógica de truncado o conflictos.
- **Solución:** Verifica `handleAppendEntries` con logs de FINE level.

### Persistencia no carga
- **Causa:** Permisos en `storage.dir` o archivos corruptos.
- **Solución:** Verifica permisos, borra `data/` para reset limpio.

---

## 10. Testing de tu Integración

```bash
# Terminal 1: Iniciar tu worker con RAFT
java -cp raft-core/out:out com.myapp.AIWorker --config config/node1.properties

# Terminal 2: Iniciar otro worker
java -cp raft-core/out:out com.myapp.AIWorker --config config/node2.properties

# Terminal 3: Enviar comando de prueba
echo "TRAIN|test-model|/data/train.csv|e30=" | nc localhost 5001

# Terminal 4: Verificar logs de ambos nodos
tail -f /tmp/node1.log /tmp/node2.log
```

---

## 11. Checklist de Integración

- [ ] Config files creados para todos los nodos
- [ ] `StateMachine` implementado y testeado
- [ ] Parsing de comandos según [`protocol.md`](protocol.md)
- [ ] Manejo de "not leader" con redirect
- [ ] Persistencia de datos de aplicación coordinada con RAFT
- [ ] Logs configurados (FINE level para debug)
- [ ] Tests con 3+ nodos en LAN/WiFi
- [ ] Script de stress test ejecutado (1000+ ops)

---

## 12. Siguientes Pasos

1. Lee [`protocol.md`](protocol.md) para comandos acordados.
2. Copia [`examples/AIWorkerExample.java`](../examples/AIWorkerExample.java) y adapta.
3. Prueba con 2 nodos locales antes de desplegar en red.
4. Consulta [`raft-core/README.md`](../raft-core/README.md) para detalles del protocolo RPC.

---

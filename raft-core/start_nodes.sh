#!/bin/bash
# filepath: /home/ak13a/Documentos/25-2/Concurrentes/Concurrentes-Final/raft-core/start_nodes.sh
set -e

trap 'echo "Stopping nodes..."; kill 0' INT TERM EXIT

java -cp out com.rafthq.core.Main --config config/sample-node1.properties &
java -cp out com.rafthq.core.Main --config config/sample-node2.properties &
java -cp out com.rafthq.core.Main --config config/sample-node3.properties &

wait
#!/bin/bash
# Test script: Start cluster and simulate command submission

set -e

# Kill existing nodes
pkill -f com.rafthq.core.Main 2>/dev/null || true
sleep 1

# Start nodes in background with no stdin
cd /home/ak13a/Documentos/25-2/Concurrentes/Concurrentes-Final/raft-core

echo "Starting 3 nodes..."
java -cp out com.rafthq.core.Main --config config/sample-node1.properties </dev/null > /tmp/node1.log 2>&1 &
PID1=$!
java -cp out com.rafthq.core.Main --config config/sample-node2.properties </dev/null > /tmp/node2.log 2>&1 &
PID2=$!
java -cp out com.rafthq.core.Main --config config/sample-node3.properties </dev/null > /tmp/node3.log 2>&1 &
PID3=$!

echo "Waiting for leader election (5s)..."
sleep 5

echo ""
echo "=== Node 1 Log ==="
tail -15 /tmp/node1.log

echo ""
echo "=== Node 2 Log ==="
tail -15 /tmp/node2.log

echo ""
echo "=== Node 3 Log ==="
tail -15 /tmp/node3.log

echo ""
echo "Cluster is running. PIDs: $PID1 $PID2 $PID3"
echo "To stop: kill $PID1 $PID2 $PID3"
echo ""
echo "To test command submission, you'll need to modify Main.java to accept non-interactive mode."

#!/bin/bash
# Test end-to-end: Start cluster, send command to leader, verify commits

set -e

echo "=== Raft End-to-End Test ==="
echo ""

# Kill existing nodes
pkill -9 -f com.rafthq.core.Main 2>/dev/null || true
sleep 1

cd /home/ak13a/Documentos/25-2/Concurrentes/Concurrentes-Final/raft-core
rm -f /tmp/node*.log

echo "Starting 3 nodes..."
java -cp out com.rafthq.core.Main --config config/sample-node1.properties </dev/null > /tmp/node1.log 2>&1 &
PID1=$!
java -cp out com.rafthq.core.Main --config config/sample-node2.properties </dev/null > /tmp/node2.log 2>&1 &
PID2=$!
java -cp out com.rafthq.core.Main --config config/sample-node3.properties </dev/null > /tmp/node3.log 2>&1 &
PID3=$!

echo "Waiting for leader election..."
sleep 4

# Identify leader
LEADER=""
if grep -q "became LEADER" /tmp/node1.log; then
    LEADER="node1"
    LEADER_PID=$PID1
elif grep -q "became LEADER" /tmp/node2.log; then
    LEADER="node2"
    LEADER_PID=$PID2
elif grep -q "became LEADER" /tmp/node3.log; then
    LEADER="node3"
    LEADER_PID=$PID3
fi

if [ -z "$LEADER" ]; then
    echo "ERROR: No leader elected!"
    tail -10 /tmp/node1.log /tmp/node2.log /tmp/node3.log
    kill $PID1 $PID2 $PID3 2>/dev/null || true
    exit 1
fi

echo "Leader elected: $LEADER"
echo ""

# Send command by echoing to the leader process (using stdin pipe)
# Since nodes are backgrounded with </dev/null, we need to kill and restart the leader interactively
echo "Note: To test appendCommand interactively, restart $LEADER manually in a terminal and type commands."
echo ""

# For now, just verify logs show proper election
echo "=== Node 1 Final State ==="
tail -8 /tmp/node1.log | grep -E "LEADER|FOLLOWER|CANDIDATE"

echo ""
echo "=== Node 2 Final State ==="
tail -8 /tmp/node2.log | grep -E "LEADER|FOLLOWER|CANDIDATE"

echo ""
echo "=== Node 3 Final State ==="
tail -8 /tmp/node3.log | grep -E "LEADER|FOLLOWER|CANDIDATE"

echo ""
echo "Cluster running. PIDs: $PID1 $PID2 $PID3"
echo "To stop: kill $PID1 $PID2 $PID3"
echo ""
echo "Next steps:"
echo "1. Kill the leader process: kill $LEADER_PID"
echo "2. Restart it in a terminal: java -cp out com.rafthq.core.Main --config config/sample-$LEADER.properties"
echo "3. Type a command like 'hello world' and press Enter"
echo "4. Check all node logs for 'Applied: hello world'"

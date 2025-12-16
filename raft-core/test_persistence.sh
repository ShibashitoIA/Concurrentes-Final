#!/bin/bash
# Test persistence: verify state survives node restart

set -e

echo "=== Raft Persistence Test ==="
echo ""

# Clean up
pkill -9 -f com.rafthq.core.Main 2>/dev/null || true
rm -rf /home/ak13a/Documentos/25-2/Concurrentes/Concurrentes-Final/raft-core/data

cd /home/ak13a/Documentos/25-2/Concurrentes/Concurrentes-Final/raft-core

echo "Step 1: Start node1 for 5 seconds (will be CANDIDATE and increment term)"
timeout 5 java -cp out com.rafthq.core.Main --config config/sample-node1.properties </dev/null > /tmp/persist_test.log 2>&1 &
PID1=$!
sleep 6

# Check persistent files
TERM1=$(cat data/node1/term.txt 2>/dev/null || echo "0")
VOTED1=$(cat data/node1/votedFor.txt 2>/dev/null || echo "none")

echo "Step 1 Result: term=$TERM1, votedFor=$VOTED1"
echo "Expected: term should be > 0 (incremented during candidate elections)"
echo ""

if [ "$TERM1" -gt 0 ]; then
    echo "✓ Persistence works! Term was saved: $TERM1"
else
    echo "✗ No persistence detected"
    exit 1
fi

echo ""
echo "Step 2: Restart node1 and verify it loads the saved term"
timeout 5 java -cp out com.rafthq.core.Main --config config/sample-node1.properties </dev/null > /tmp/persist_test2.log 2>&1 &
PID2=$!
sleep 2

# Check that startup log shows loaded term
if grep -q "Loaded: term=$TERM1" /tmp/persist_test2.log; then
    echo "✓ Node reloaded correct term: $TERM1"
else
    echo "✗ Node did not load term correctly"
    echo "Log:"
    cat /tmp/persist_test2.log | grep -i "loaded\|term"
    exit 1
fi

echo ""
echo "=== All persistence tests passed! ==="
kill $PID2 2>/dev/null || true

package main

import (
	"encoding/json"
	"fmt"
	"net/http"
)

func StartHTTP(node *RaftNode) {
	http.HandleFunc("/status", func(w http.ResponseWriter, _ *http.Request) {
		node.mu.Lock()
		defer node.mu.Unlock()

		json.NewEncoder(w).Encode(map[string]interface{}{
			"node":   node.NodeID,
			"role":   node.Role,
			"term":   node.CurrentTerm,
			"leader": node.LeaderID,
		})
	})

	http.HandleFunc("/log", func(w http.ResponseWriter, _ *http.Request) {
		node.mu.Lock()
		defer node.mu.Unlock()
		json.NewEncoder(w).Encode(node.Log)
	})

	http.ListenAndServe(
		fmt.Sprintf("%s:%d", Host, HttpPort),
		nil,
	)
}

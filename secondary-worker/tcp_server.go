package main

import (
	"encoding/json"
	"fmt"
	"net"
)

func StartTCPServer(node *RaftNode) {
	addr := fmt.Sprintf("%s:%d", Host, RaftPort)
	ln, _ := net.Listen("tcp", addr)
	fmt.Println("[RAFT] Listening on", addr)

	for {
		conn, _ := ln.Accept()
		go handleConn(conn, node)
	}
}

func handleConn(conn net.Conn, node *RaftNode) {
	defer conn.Close()

	buf := make([]byte, 8192)
	n, _ := conn.Read(buf)

	var msg map[string]interface{}
	json.Unmarshal(buf[:n], &msg)

	var resp map[string]interface{}

	switch msg["type"] {

	case "REQUEST_VOTE":
		resp = node.HandleRequestVote(msg)

	case "APPEND_ENTRIES":
		resp = node.HandleAppendEntries(msg)

	default:
		resp = map[string]interface{}{"error": "unknown"}
	}

	out, _ := json.Marshal(resp)
	conn.Write(out)
}

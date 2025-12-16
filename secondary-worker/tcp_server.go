package main

import (
	"bufio"
	"encoding/base64"
	"fmt"
	"net"
	"strconv"
	"strings"
)

func StartTCPServer(node *RaftNode) {
	addr := fmt.Sprintf("%s:%d", Host, RaftPort)
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		fmt.Println("[ERROR] Failed to listen:", err)
		return
	}
	fmt.Println("[RAFT] Listening on", addr)

	for {
		conn, err := ln.Accept()
		if err != nil {
			continue
		}
		go handleConn(conn, node)
	}
}

func handleConn(conn net.Conn, node *RaftNode) {
	defer conn.Close()

	reader := bufio.NewReader(conn)
	// Leemos hasta salto de línea (protocolo Java usa println)
	line, err := reader.ReadString('\n')
	if err != nil {
		return
	}
	line = strings.TrimSpace(line)
	parts := strings.Split(line, "|")

	if len(parts) == 0 {
		return
	}

	var response string

	switch parts[0] {
	case "REQUEST_VOTE":
		// Format: REQUEST_VOTE|term|candidateId|lastLogIndex|lastLogTerm
		if len(parts) < 5 {
			return
		}
		term, _ := strconv.Atoi(parts[1])
		candidateID := parts[2]
		lastLogIndex, _ := strconv.Atoi(parts[3])
		lastLogTerm, _ := strconv.Atoi(parts[4])

		currentTerm, granted := node.HandleRequestVote(term, candidateID, lastLogIndex, lastLogTerm)
		// Format: REQUEST_VOTE_RESPONSE|term|voteGranted
		response = fmt.Sprintf("REQUEST_VOTE_RESPONSE|%d|%t", currentTerm, granted)

	case "APPEND_ENTRIES":
		// Format: APPEND_ENTRIES|term|leaderId|prevLogIndex|prevLogTerm|leaderCommit|numEntries|entry1...
		if len(parts) < 7 {
			return
		}
		term, _ := strconv.Atoi(parts[1])
		leaderID := parts[2]
		prevLogIndex, _ := strconv.Atoi(parts[3])
		prevLogTerm, _ := strconv.Atoi(parts[4])
		leaderCommit, _ := strconv.Atoi(parts[5])
		numEntries, _ := strconv.Atoi(parts[6])

		var entries []LogEntry
		// Parsear entradas
		for i := 0; i < numEntries; i++ {
			if 7+i >= len(parts) {
				break
			}
			// Entry format in Java: index,term,length,base64payload
			entryParts := strings.SplitN(parts[7+i], ",", 4)
			if len(entryParts) < 4 {
				continue
			}
			idx, _ := strconv.Atoi(entryParts[0])
			t, _ := strconv.Atoi(entryParts[1])
			payload, _ := base64.StdEncoding.DecodeString(entryParts[3])

			entries = append(entries, LogEntry{
				Index:   idx,
				Term:    t,
				Payload: payload,
			})
		}

		currentTerm, success, matchIndex := node.HandleAppendEntries(term, leaderID, prevLogIndex, prevLogTerm, leaderCommit, entries)
		// Format: APPEND_ENTRIES_RESPONSE|term|success|matchIndex
		response = fmt.Sprintf("APPEND_ENTRIES_RESPONSE|%d|%t|%d", currentTerm, success, matchIndex)

	default:
		// Podría ser un comando de cliente si decidimos soportarlo, o error
		fmt.Println("[WARN] Unknown message:", parts[0])
		return
	}

	conn.Write([]byte(response + "\n"))
}

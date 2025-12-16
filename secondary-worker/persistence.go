package main

import (
	"bufio"
	"encoding/base64"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

type PersistentState struct {
	Dir string
}

func NewPersistentState(dir string) *PersistentState {
	os.MkdirAll(dir, 0755)
	return &PersistentState{Dir: dir}
}

func (p *PersistentState) SaveTerm(term int) {
	os.WriteFile(filepath.Join(p.Dir, "term.txt"), []byte(strconv.Itoa(term)), 0644)
}

func (p *PersistentState) LoadTerm() int {
	data, err := os.ReadFile(filepath.Join(p.Dir, "term.txt"))
	if err != nil {
		return 0
	}
	t, _ := strconv.Atoi(strings.TrimSpace(string(data)))
	return t
}

func (p *PersistentState) SaveVotedFor(candidateID string) {
	if candidateID == "" {
		os.Remove(filepath.Join(p.Dir, "votedFor.txt"))
	} else {
		os.WriteFile(filepath.Join(p.Dir, "votedFor.txt"), []byte(candidateID), 0644)
	}
}

func (p *PersistentState) LoadVotedFor() string {
	data, err := os.ReadFile(filepath.Join(p.Dir, "votedFor.txt"))
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(data))
}

func (p *PersistentState) AppendLogEntry(e LogEntry) {
	f, _ := os.OpenFile(filepath.Join(p.Dir, "log.txt"), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	defer f.Close()

	b64 := base64.StdEncoding.EncodeToString(e.Payload)
	line := fmt.Sprintf("%d,%d,%s\n", e.Index, e.Term, b64)
	f.WriteString(line)
}

func (p *PersistentState) LoadLog() []LogEntry {
	var entries []LogEntry
	// Agregar entrada dummy (index 0) para coincidir con la lógica 1-based de Raft
	entries = append(entries, LogEntry{Index: 0, Term: 0})

	f, err := os.Open(filepath.Join(p.Dir, "log.txt"))
	if err != nil {
		return entries
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := scanner.Text()
		if line == "" {
			continue
		}
		parts := strings.Split(line, ",")
		idx, _ := strconv.Atoi(parts[0])
		term, _ := strconv.Atoi(parts[1])
		payload, _ := base64.StdEncoding.DecodeString(parts[2])
		entries = append(entries, LogEntry{Index: idx, Term: term, Payload: payload})
	}
	return entries
}

// Truncar log (para cuando hay conflictos)
func (p *PersistentState) TruncateLog(fromIndex int, currentLog []LogEntry) {
	// Reescribir archivo completo
	f, _ := os.Create(filepath.Join(p.Dir, "log.txt"))
	defer f.Close()

	for _, e := range currentLog {
		if e.Index == 0 {
			continue
		} // saltar dummy
		if e.Index >= fromIndex {
			break
		} // hemos truncado memoria, truncar disco

		b64 := base64.StdEncoding.EncodeToString(e.Payload)
		line := fmt.Sprintf("%d,%d,%s\n", e.Index, e.Term, b64)
		f.WriteString(line)
	}
}

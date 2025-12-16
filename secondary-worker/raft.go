package main

import (
	"fmt"
	"sync"
)

const (
	Follower  = "FOLLOWER"
	Candidate = "CANDIDATE"
	Leader    = "LEADER"
)

// LogEntry representa una entrada en el log de Raft
type LogEntry struct {
	Index   int
	Term    int
	Payload []byte // Contenido crudo del comando
}

type RaftNode struct {
	mu          sync.Mutex
	NodeID      string
	CurrentTerm int
	VotedFor    string
	Log         []LogEntry
	CommitIndex int
	LastApplied int
	Role        string
	LeaderID    string
}

func NewRaftNode(id string) *RaftNode {
	// Inicializamos con un log dummy en índice 0 (igual que Java)
	dummyLog := make([]LogEntry, 0)
	dummyLog = append(dummyLog, LogEntry{Index: 0, Term: 0, Payload: []byte{}})

	return &RaftNode{
		NodeID:      id,
		CurrentTerm: 0,
		VotedFor:    "",
		Log:         dummyLog,
		CommitIndex: 0,
		LastApplied: 0,
		Role:        Follower,
	}
}

// getLastLogInfo retorna índice y término de la última entrada
func (r *RaftNode) getLastLogInfo() (int, int) {
	if len(r.Log) == 0 {
		return 0, 0
	}
	last := r.Log[len(r.Log)-1]
	return last.Index, last.Term
}

// getLogTerm retorna el término de un índice específico
func (r *RaftNode) getLogTerm(index int) int {
	if index < 0 || index >= len(r.Log) {
		return -1
	}
	return r.Log[index].Term
}

// HandleRequestVote procesa solicitudes de voto
func (r *RaftNode) HandleRequestVote(term int, candidateID string, lastLogIndex int, lastLogTerm int) (int, bool) {
	r.mu.Lock()
	defer r.mu.Unlock()

	// 1. Si el término es mayor, actualizar y volverse Follower
	if term > r.CurrentTerm {
		r.CurrentTerm = term
		r.VotedFor = ""
		r.Role = Follower
	}

	// 2. Responder falso si el término es antiguo
	if term < r.CurrentTerm {
		return r.CurrentTerm, false
	}

	// 3. Verificar si ya votamos
	canVote := (r.VotedFor == "" || r.VotedFor == candidateID)

	// 4. Verificar si el log del candidato está al día
	myLastIndex, myLastTerm := r.getLastLogInfo()
	logIsUpToDate := false
	if lastLogTerm > myLastTerm {
		logIsUpToDate = true
	} else if lastLogTerm == myLastTerm && lastLogIndex >= myLastIndex {
		logIsUpToDate = true
	}

	if canVote && logIsUpToDate {
		r.VotedFor = candidateID
		fmt.Printf("[RAFT] Vote GRANTED to %s for term %d\n", candidateID, term)
		return r.CurrentTerm, true
	}

	return r.CurrentTerm, false
}

// HandleAppendEntries procesa la replicación de logs y heartbeats
func (r *RaftNode) HandleAppendEntries(term int, leaderID string, prevLogIndex int, prevLogTerm int, leaderCommit int, entries []LogEntry) (int, bool, int) {
	r.mu.Lock()
	defer r.mu.Unlock()

	// 1. Actualizar término y líder
	if term > r.CurrentTerm {
		r.CurrentTerm = term
		r.VotedFor = ""
		r.Role = Follower
	}
	r.LeaderID = leaderID

	// 2. Responder falso si término es antiguo
	if term < r.CurrentTerm {
		return r.CurrentTerm, false, r.getLastIndex()
	}

	// Reset election timer (To be implemented with channels/timers in main loop)
	// fmt.Printf("[RAFT] Heartbeat from %s\n", leaderID)

	// 3. Verificar consistencia del log (PrevLogIndex/Term)
	if prevLogIndex >= len(r.Log) {
		// No tenemos el log previo
		return r.CurrentTerm, false, r.getLastIndex()
	}
	if r.Log[prevLogIndex].Term != prevLogTerm {
		// Conflicto de término
		// Truncar log desde el conflicto (simplificado)
		r.Log = r.Log[:prevLogIndex]
		return r.CurrentTerm, false, r.getLastIndex()
	}

	// 4. Anexar nuevas entradas
	for _, entry := range entries {
		// Si es una entrada nueva o sobreescribe
		if entry.Index < len(r.Log) {
			if r.Log[entry.Index].Term != entry.Term {
				r.Log = r.Log[:entry.Index] // Truncar conflicto
				r.Log = append(r.Log, entry)
			}
		} else {
			r.Log = append(r.Log, entry)
		}
	}

	// 5. Actualizar CommitIndex
	if leaderCommit > r.CommitIndex {
		lastIdx := r.getLastIndex()
		if leaderCommit < lastIdx {
			r.CommitIndex = leaderCommit
		} else {
			r.CommitIndex = lastIdx
		}
		r.applyLogs()
	}

	return r.CurrentTerm, true, r.getLastIndex()
}

func (r *RaftNode) getLastIndex() int {
	if len(r.Log) == 0 {
		return 0
	}
	return r.Log[len(r.Log)-1].Index
}

// applyLogs aplica entradas confirmadas a la máquina de estados
func (r *RaftNode) applyLogs() {
	for r.LastApplied < r.CommitIndex {
		r.LastApplied++
		entry := r.Log[r.LastApplied]
		// Llamar a la State Machine
		Apply(entry.Payload)
		fmt.Printf("[RAFT] Applied index %d\n", r.LastApplied)
	}
}

package main

import "sync"

const (
	Follower  = "FOLLOWER"
	Candidate = "CANDIDATE"
	Leader    = "LEADER"
)

type LogEntry map[string]interface{}

type RaftNode struct {
	mu          sync.Mutex
	NodeID      string
	CurrentTerm int
	VotedFor    string
	Log         []LogEntry
	CommitIndex int
	Role        string
	LeaderID    string
}

func NewRaftNode(id string) *RaftNode {
	return &RaftNode{
		NodeID:      id,
		CurrentTerm: 0,
		CommitIndex: -1,
		Role:        Follower,
		Log:         make([]LogEntry, 0),
	}
}

func (r *RaftNode) HandleRequestVote(msg map[string]interface{}) map[string]interface{} {
	r.mu.Lock()
	defer r.mu.Unlock()

	term := int(msg["term"].(float64))
	if term < r.CurrentTerm {
		return map[string]interface{}{
			"term":        r.CurrentTerm,
			"voteGranted": false,
		}
	}

	r.CurrentTerm = term
	r.VotedFor = msg["candidateId"].(string)
	r.Role = Follower

	return map[string]interface{}{
		"term":        r.CurrentTerm,
		"voteGranted": true,
	}
}

func (r *RaftNode) HandleAppendEntries(msg map[string]interface{}) map[string]interface{} {
	r.mu.Lock()
	defer r.mu.Unlock()

	term := int(msg["term"].(float64))
	if term < r.CurrentTerm {
		return map[string]interface{}{
			"term":    r.CurrentTerm,
			"success": false,
		}
	}

	r.CurrentTerm = term
	r.LeaderID = msg["leaderId"].(string)
	r.Role = Follower

	if entries, ok := msg["entries"].([]interface{}); ok {
		for _, e := range entries {
			entry := e.(map[string]interface{})
			r.Log = append(r.Log, entry)
			r.CommitIndex++
			Apply(entry)
		}
	}

	return map[string]interface{}{
		"term":    r.CurrentTerm,
		"success": true,
	}
}

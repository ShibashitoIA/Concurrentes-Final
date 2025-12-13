package main

const (
	NodeID   = "go-worker-1"
	Host     = "0.0.0.0"
	RaftPort = 9004
	HttpPort = 8004
)

var Peers = []string{
	"127.0.0.1:9001",
	"127.0.0.1:9002",
}

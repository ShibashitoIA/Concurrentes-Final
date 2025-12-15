package main

func main() {
	node := NewRaftNode(NodeID)

	go StartTCPServer(node)
	go StartHTTP(node)

	println("[OK] Go worker running")
	select {}
}

package main

import (
	"encoding/json"
	"fmt"
	"math/rand"
	"net"
	"os"
	"sync"
	"time"
)

const (
	TargetHost = "127.0.0.1:9001"
	Threads    = 50
	Requests   = 1000
)

type Result struct {
	Timestamp float64
	Operation string
	Latency   float64
	Success   bool
}

var (
	results []Result
	mu      sync.Mutex
)

func sendRequest(id int) {
	msg := map[string]interface{}{
		"type":     "APPEND_ENTRIES",
		"term":     1,
		"leaderId": "stress-go",
		"entries": []map[string]interface{}{
			randomEntry(id),
		},
	}

	start := time.Now()
	success := true

	conn, err := net.DialTimeout("tcp", TargetHost, 2*time.Second)
	if err != nil {
		success = false
	} else {
		data, _ := json.Marshal(msg)
		conn.Write(data)
		conn.Read(make([]byte, 4096))
		conn.Close()
	}

	latency := float64(time.Since(start).Milliseconds())

	mu.Lock()
	results = append(results, Result{
		Timestamp: float64(time.Now().Unix()),
		Operation: msg["entries"].([]map[string]interface{})[0]["cmd"].(string),
		Latency:   latency,
		Success:   success,
	})
	mu.Unlock()
}

func randomEntry(id int) map[string]interface{} {
	if rand.Intn(2) == 0 {
		return map[string]interface{}{
			"cmd":     "REGISTER_MODEL",
			"modelId": fmt.Sprintf("m%d", id),
		}
	}
	return map[string]interface{}{
		"cmd":  "STORE_FILE",
		"file": fmt.Sprintf("f%d.txt", id),
	}
}

func main() {
	rand.Seed(time.Now().UnixNano())
	wg := sync.WaitGroup{}

	start := time.Now()

	for i := 0; i < Threads; i++ {
		wg.Add(1)
		go func(base int) {
			defer wg.Done()
			for j := 0; j < Requests/Threads; j++ {
				sendRequest(base + j)
			}
		}(i * (Requests / Threads))
	}

	wg.Wait()
	elapsed := time.Since(start).Seconds()
	fmt.Printf("Throughput: %.2f ops/sec\n", float64(Requests)/elapsed)

	f, _ := os.Create("results.csv")
	defer f.Close()
	fmt.Fprintln(f, "timestamp,operation,latency_ms,success")

	for _, r := range results {
		fmt.Fprintf(f, "%.0f,%s,%.2f,%t\n",
			r.Timestamp, r.Operation, r.Latency, r.Success)
	}
}

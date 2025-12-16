package main

import (
	"encoding/base64"
	"fmt"
	"math/rand"
	"net"
	"os"
	"sync"
	"time"
)

// Apuntar al puerto RPC del worker Go o Java
const (
	TargetHost = "127.0.0.1:9003" // Puerto del worker Go
	Threads    = 20
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

// buildCommand crea un string compatible con el protocolo
func buildCommand(id int) string {
	if rand.Intn(2) == 0 {
		// REGISTER_MODEL|id|type|accuracy|ts
		return fmt.Sprintf("REGISTER_MODEL|model_stress_%d|MLP|0.99|%d", id, time.Now().Unix())
	} else {
		// STORE_FILE|name|checksum|size|contentB64
		content := "dummy_content"
		b64 := base64.StdEncoding.EncodeToString([]byte(content))
		return fmt.Sprintf("STORE_FILE|file_stress_%d.txt|0000|%d|%s", id, len(content), b64)
	}
}

func sendRequest(id int) {
	// NOTA: En un cluster Raft real, los clientes envían comandos al Líder.
	// Aquí simulamos el envío de una petición. Si el nodo es seguidor, podría fallar o redirigir.
	// Para estresar al worker Go, le enviaremos comandos "simulados" como si fueran Appends vacíos o data.
	// PERO, para cumplir con el requisito de "Enviar 1000+ operaciones", intentaremos
	// enviar el comando crudo.

	cmd := buildCommand(id)

	// Envolver en APPEND_ENTRIES para que el worker Go lo procese (simulando ser el Líder)
	// APPEND_ENTRIES|term|leaderId|prevLogIndex|prevLogTerm|leaderCommit|numEntries|entry
	// Entry: index,term,len,base64Payload

	payloadB64 := base64.StdEncoding.EncodeToString([]byte(cmd))
	// Simulamos ser un líder con termino alto para forzar aceptación o log simple
	entry := fmt.Sprintf("%d,%d,%d,%s", id, 999, len(cmd), payloadB64)

	msg := fmt.Sprintf("APPEND_ENTRIES|999|stress-test|0|0|0|1|%s\n", entry)

	start := time.Now()
	success := true

	conn, err := net.DialTimeout("tcp", TargetHost, 2*time.Second)
	if err != nil {
		success = false
		// fmt.Println("Connect error:", err)
	} else {
		conn.Write([]byte(msg))
		// Leer respuesta
		buf := make([]byte, 1024)
		conn.SetReadDeadline(time.Now().Add(2 * time.Second))
		_, err := conn.Read(buf)
		if err != nil {
			success = false
		}
		conn.Close()
	}

	latency := float64(time.Since(start).Milliseconds())

	opType := "STORE_FILE"
	if len(cmd) > 14 && cmd[:14] == "REGISTER_MODEL" {
		opType = "REGISTER_MODEL"
	}

	mu.Lock()
	results = append(results, Result{
		Timestamp: float64(time.Now().Unix()),
		Operation: opType,
		Latency:   latency,
		Success:   success,
	})
	mu.Unlock()
}

func main() {
	fmt.Printf("Starting Stress Test against %s\n", TargetHost)
	rand.Seed(time.Now().UnixNano())
	wg := sync.WaitGroup{}

	start := time.Now()

	for i := 0; i < Threads; i++ {
		wg.Add(1)
		go func(base int) {
			defer wg.Done()
			for j := 0; j < Requests/Threads; j++ {
				sendRequest(base + j)
				time.Sleep(5 * time.Millisecond) // Pequeño delay para no saturar socket local
			}
		}(i * (Requests / Threads))
	}

	wg.Wait()
	elapsed := time.Since(start).Seconds()
	fmt.Printf("Throughput: %.2f ops/sec\n", float64(Requests)/elapsed)

	f, _ := os.Create("stress_results.csv")
	defer f.Close()
	fmt.Fprintln(f, "timestamp,operation,latency_ms,success")

	successCount := 0
	for _, r := range results {
		if r.Success {
			successCount++
		}
		fmt.Fprintf(f, "%.0f,%s,%.2f,%t\n",
			r.Timestamp, r.Operation, r.Latency, r.Success)
	}
	fmt.Printf("Success Rate: %d/%d\n", successCount, Requests)
}

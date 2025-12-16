package main

import (
	"encoding/base64"
	"fmt"
	"os"
	"strings"
)

// Directorio donde este worker guardará sus archivos replicados
const DataDir = "data/go-worker-1"

func Apply(payload []byte) {
	// Asegurar que existe el directorio
	err := os.MkdirAll(DataDir, 0755)
	if err != nil {
		fmt.Printf("[APP] Error creando directorio %s: %v\n", DataDir, err)
		return
	}

	// Convertir payload a string y separar por tuberías
	cmdStr := string(payload)
	parts := strings.Split(cmdStr, "|")

	if len(parts) == 0 {
		return
	}

	cmd := parts[0]
	fmt.Printf("[APP] Ejecutando comando real: %s\n", cmd)

	switch cmd {
	case "STORE_FILE":
		// Formato: STORE_FILE|fileName|checksum|size|contentBase64
		if len(parts) < 5 {
			fmt.Println("[APP] Error: STORE_FILE con argumentos insuficientes")
			return
		}
		fileName := parts[1]
		// checksum := parts[2] // Podrías validar el MD5 aquí si quisieras
		// size := parts[3]
		contentB64 := parts[4]

		// 1. Decodificar el contenido real
		decodedContent, err := base64.StdEncoding.DecodeString(contentB64)
		if err != nil {
			fmt.Printf("[APP] Error decodificando Base64 para %s: %v\n", fileName, err)
			return
		}

		// 2. Escribir el contenido exacto al disco
		filePath := fmt.Sprintf("%s/%s", DataDir, fileName)
		f, err := os.Create(filePath)
		if err != nil {
			fmt.Printf("[APP] Error creando archivo %s: %v\n", filePath, err)
			return
		}
		defer f.Close()

		_, err = f.Write(decodedContent)
		if err != nil {
			fmt.Printf("[APP] Error escribiendo en %s: %v\n", filePath, err)
		} else {
			fmt.Printf("[APP] Archivo guardado exitosamente: %s (%d bytes)\n", fileName, len(decodedContent))
		}

	case "REGISTER_MODEL":
		// Formato: REGISTER_MODEL|modelId|type|accuracy|timestamp
		if len(parts) < 5 {
			fmt.Println("[APP] Error: REGISTER_MODEL con argumentos insuficientes")
			return
		}
		modelID := parts[1]
		modelType := parts[2]
		accuracy := parts[3]
		timestamp := parts[4]

		// 3. Registrar toda la metadata en el archivo de registro
		logPath := fmt.Sprintf("%s/models_registry.txt", DataDir)
		f, err := os.OpenFile(logPath, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
		if err != nil {
			fmt.Printf("[APP] Error abriendo registro de modelos: %v\n", err)
			return
		}
		defer f.Close()

		// Guardar una línea estructurada (CSV)
		record := fmt.Sprintf("%s,%s,%s,%s\n", modelID, modelType, accuracy, timestamp)
		f.WriteString(record)
		fmt.Printf("[APP] Modelo registrado: %s (Type: %s, Acc: %s)\n", modelID, modelType, accuracy)

	default:
		// Otros comandos (TRAIN, PREDICT) pueden ser ignorados por el Storage Worker
		// o implementados si este worker tuviera capacidades de IA.
	}
}

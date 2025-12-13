package main

import (
	"fmt"
	"os"
)

const DataDir = "data"

func Apply(entry map[string]interface{}) {
	os.MkdirAll(DataDir, 0755)

	cmd := entry["cmd"].(string)

	switch cmd {

	case "STORE_FILE":
		file := entry["file"].(string)
		f, _ := os.Create(fmt.Sprintf("%s/%s", DataDir, file))
		f.WriteString("dummy content")
		f.Close()

	case "REGISTER_MODEL":
		model := entry["modelId"].(string)
		f, _ := os.OpenFile(
			fmt.Sprintf("%s/models.txt", DataDir),
			os.O_APPEND|os.O_CREATE|os.O_WRONLY,
			0644,
		)
		f.WriteString(model + "\n")
		f.Close()
	}
}

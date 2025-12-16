package com.mycompany.moduloia.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ModelRegistry {

    private final Path indexFile;
    private final Map<String, String> map = new HashMap<>();

    public ModelRegistry(Path indexFile) {
        this.indexFile = indexFile;
        load();
    }

    public synchronized void put(String modelId, String filename) {
        map.put(modelId, filename);
        save();
    }

    public synchronized String get(String modelId) {
        return map.get(modelId);
    }

    private void load() {
        try {
            if (!Files.exists(indexFile)) {
                return;
            }
            try (BufferedReader br = new BufferedReader(new FileReader(indexFile.toFile()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    int sep = line.indexOf('\t');
                    if (sep < 0) {
                        continue;
                    }
                    String id = line.substring(0, sep).trim();
                    String fn = line.substring(sep + 1).trim();
                    if (!id.isEmpty() && !fn.isEmpty()) {
                        map.put(id, fn);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load model registry: " + indexFile, e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(indexFile.getParent());
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(indexFile.toFile()))) {
                for (Map.Entry<String, String> e : map.entrySet()) {
                    bw.write(e.getKey());
                    bw.write('\t');
                    bw.write(e.getValue());
                    bw.newLine();
                }
                bw.flush();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save model registry: " + indexFile, e);
        }
    }
}

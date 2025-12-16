package com.mainworker.core;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Base64;

/**
 * Gestor de archivos replicados
 * Maneja el almacenamiento, lectura y verificación de archivos
 */
public class FileManager {

    private static final Logger LOGGER = Logger.getLogger(FileManager.class.getName());

    private final Path dataDirectory;
    private final ConcurrentHashMap<String, FileMetadata> fileIndex;

    public FileManager(String dataDirectoryPath) throws IOException {
        this.dataDirectory = Paths.get(dataDirectoryPath);
        this.fileIndex = new ConcurrentHashMap<>();

        // Crear directorio si no existe
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
            LOGGER.info("Created data directory: " + dataDirectory.toAbsolutePath());
        }

        // Cargar índice de archivos existentes
        loadFileIndex();
    }

    /**
     * Almacena un archivo en disco
     */
    public void storeFile(String fileName, String dataBase64, String expectedChecksum, long expectedSize) throws IOException {
        byte[] data = Base64.getDecoder().decode(dataBase64);

        // Verificar tamaño
        if (data.length != expectedSize) {
            throw new IOException("Size mismatch: expected " + expectedSize + " but got " + data.length);
        }

        // Verificar checksum MD5
        String actualChecksum = calculateMD5(data);
        if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
            throw new IOException("Checksum mismatch: expected " + expectedChecksum + " but got " + actualChecksum);
        }

        // Guardar archivo
        Path filePath = dataDirectory.resolve(fileName);

        // Crear subdirectorios si es necesario
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        Files.write(filePath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Actualizar índice
        FileMetadata metadata = new FileMetadata(
            fileName,
            data.length,
            actualChecksum,
            System.currentTimeMillis()
        );
        fileIndex.put(fileName, metadata);

        LOGGER.info("File stored: " + fileName + " (" + data.length + " bytes, MD5=" + actualChecksum + ")");
    }

    /**
     * Lee un archivo del disco
     */
    public byte[] readFile(String fileName) throws IOException {
        Path filePath = dataDirectory.resolve(fileName);

        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found: " + fileName);
        }

        return Files.readAllBytes(filePath);
    }

    /**
     * Obtiene la ruta absoluta de un archivo
     */
    public String getFilePath(String fileName) {
        return dataDirectory.resolve(fileName).toAbsolutePath().toString();
    }

    /**
     * Elimina un archivo
     */
    public void deleteFile(String fileName) throws IOException {
        Path filePath = dataDirectory.resolve(fileName);

        if (Files.exists(filePath)) {
            Files.delete(filePath);
            fileIndex.remove(fileName);
            LOGGER.info("File deleted: " + fileName);
        } else {
            LOGGER.warning("Attempted to delete non-existent file: " + fileName);
        }
    }

    /**
     * Lista archivos que coinciden con un patrón
     */
    public List<FileMetadata> listFiles(String pattern) {
        if (pattern.equals("*")) {
            return new ArrayList<>(fileIndex.values());
        }

        List<FileMetadata> matchingFiles = new ArrayList<>();
        String regex = pattern.replace("*", ".*").replace("?", ".");

        for (Map.Entry<String, FileMetadata> entry : fileIndex.entrySet()) {
            if (entry.getKey().matches(regex)) {
                matchingFiles.add(entry.getValue());
            }
        }

        return matchingFiles;
    }

    /**
     * Obtiene metadata de un archivo
     */
    public FileMetadata getFileMetadata(String fileName) {
        return fileIndex.get(fileName);
    }

    /**
     * Obtiene todos los archivos
     */
    public Map<String, FileMetadata> getAllFiles() {
        return new HashMap<>(fileIndex);
    }

    /**
     * Calcula MD5 checksum
     */
    private String calculateMD5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate MD5", e);
        }
    }

    /**
     * Carga el índice de archivos desde disco
     */
    private void loadFileIndex() {
        try {
            if (!Files.exists(dataDirectory)) {
                return;
            }

            Files.walk(dataDirectory)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String fileName = dataDirectory.relativize(path).toString().replace("\\", "/");
                        byte[] data = Files.readAllBytes(path);
                        String checksum = calculateMD5(data);
                        long timestamp = Files.getLastModifiedTime(path).toMillis();

                        FileMetadata metadata = new FileMetadata(
                            fileName,
                            data.length,
                            checksum,
                            timestamp
                        );
                        fileIndex.put(fileName, metadata);

                        LOGGER.fine("Indexed file: " + fileName);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to index file: " + path, e);
                    }
                });

            LOGGER.info("Loaded " + fileIndex.size() + " files into index");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load file index", e);
        }
    }

    /**
     * Clase interna para metadata de archivos
     */
    public static class FileMetadata {
        private final String fileName;
        private final long sizeBytes;
        private final String checksumMD5;
        private final long timestampEpoch;

        public FileMetadata(String fileName, long sizeBytes, String checksumMD5, long timestampEpoch) {
            this.fileName = fileName;
            this.sizeBytes = sizeBytes;
            this.checksumMD5 = checksumMD5;
            this.timestampEpoch = timestampEpoch;
        }

        public String getFileName() {
            return fileName;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public String getChecksumMD5() {
            return checksumMD5;
        }

        public long getTimestampEpoch() {
            return timestampEpoch;
        }

        public String toJson() {
            return String.format(
                "{\"fileName\":\"%s\",\"sizeBytes\":%d,\"checksumMD5\":\"%s\",\"timestamp\":%d}",
                fileName, sizeBytes, checksumMD5, timestampEpoch
            );
        }
    }
}

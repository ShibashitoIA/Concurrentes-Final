package com.raft.client.network;

import com.google.gson.Gson;
import com.raft.client.protocol.Command;
import com.raft.client.protocol.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Cliente de red que maneja la comunicación con el servidor RAFT.
 * Implementa lógica de redirección automática al líder.
 */
public class NetworkClient {
    private static final Logger logger = LoggerFactory.getLogger(NetworkClient.class);
    private static final int MAX_REDIRECTS = 5;
    private static final int TIMEOUT_MS = 10000; // 10 segundos
    
    private final Gson gson;
    private String currentHost;
    private int currentPort;

    public NetworkClient(String initialHost, int initialPort) {
        this.currentHost = initialHost;
        this.currentPort = initialPort;
        this.gson = new Gson();
    }

    /**
     * Envía un comando al servidor con manejo automático de redirección.
     */
    public Response sendCommand(Command command) throws IOException {
        return sendCommandWithRedirect(command, 0);
    }

    private Response sendCommandWithRedirect(Command command, int redirectCount) throws IOException {
        if (redirectCount >= MAX_REDIRECTS) {
            throw new IOException("Demasiadas redirecciones. El cluster puede estar inestable.");
        }

        logger.info("Enviando comando {} a {}:{}", command.getType(), currentHost, currentPort);

        try (Socket socket = new Socket(currentHost, currentPort)) {
            socket.setSoTimeout(TIMEOUT_MS);

            // Enviar comando
            OutputStream out = socket.getOutputStream();
            DataOutputStream dos = new DataOutputStream(out);

            // Serializar comando a JSON
            String jsonCommand = gson.toJson(command);
            byte[] jsonBytes = jsonCommand.getBytes(StandardCharsets.UTF_8);

            // Protocolo: [longitud del JSON][JSON][datos binarios si existen]
            dos.writeInt(jsonBytes.length);
            dos.write(jsonBytes);

            // Si hay datos binarios, enviarlos
            if (command.getData() != null && command.getData().length > 0) {
                dos.writeInt(command.getData().length);
                dos.write(command.getData());
            } else {
                dos.writeInt(0);
            }
            dos.flush();

            // Recibir respuesta
            InputStream in = socket.getInputStream();
            DataInputStream dis = new DataInputStream(in);

            int responseLength = dis.readInt();
            if (responseLength <= 0 || responseLength > 10_000_000) { // Límite de seguridad 10MB
                throw new IOException("Longitud de respuesta inválida: " + responseLength);
            }

            byte[] responseBytes = new byte[responseLength];
            dis.readFully(responseBytes);

            String jsonResponse = new String(responseBytes, StandardCharsets.UTF_8);
            Response response = gson.fromJson(jsonResponse, Response.class);

            // Leer datos binarios si existen (para descargas de archivos)
            int dataLength = dis.readInt();
            if (dataLength > 0) {
                if (dataLength > 100_000_000) { // Límite de seguridad 100MB
                    throw new IOException("Archivo demasiado grande: " + dataLength + " bytes");
                }
                byte[] binaryData = new byte[dataLength];
                dis.readFully(binaryData);
                response.setBinaryData(binaryData);
                logger.info("Datos binarios recibidos: {} bytes", dataLength);
            }

            logger.info("Respuesta recibida: {}", response);

            // Manejar redirección
            if (response.isRedirect() && response.getLeaderHost() != null) {
                logger.info("Redirigiendo al líder: {}:{}", 
                    response.getLeaderHost(), response.getLeaderPort());
                
                this.currentHost = response.getLeaderHost();
                this.currentPort = response.getLeaderPort();
                
                // Reintentar con el nuevo líder
                return sendCommandWithRedirect(command, redirectCount + 1);
            }

            return response;

        } catch (IOException e) {
            logger.error("Error al comunicarse con {}:{}", currentHost, currentPort, e);
            throw new IOException("Error de conexión: " + e.getMessage(), e);
        }
    }

    /**
     * Verifica la conexión con el servidor actual.
     */
    public boolean testConnection() {
        try (Socket socket = new Socket(currentHost, currentPort)) {
            socket.setSoTimeout(3000);
            return true;
        } catch (IOException e) {
            logger.warn("No se pudo conectar a {}:{}", currentHost, currentPort);
            return false;
        }
    }

    /**
     * Actualiza el servidor al que se conecta.
     */
    public void setServer(String host, int port) {
        this.currentHost = host;
        this.currentPort = port;
        logger.info("Servidor actualizado a {}:{}", host, port);
    }

    public String getCurrentHost() {
        return currentHost;
    }

    public int getCurrentPort() {
        return currentPort;
    }

    public String getCurrentServer() {
        return currentHost + ":" + currentPort;
    }
}

package com.raft.client.network;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.raft.client.protocol.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Cliente HTTP para el monitor del main-worker.
 * Envía comandos como texto (OP|arg1|...) a POST /command y
 * consulta estado/modelos vía GET.
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

    private String baseUrl() {
        return "http://" + currentHost + ":" + currentPort;
    }

    /**
     * Envía un comando de texto al monitor: POST /command
     */
    public Response sendCommandText(String commandLine) throws IOException {
        return sendCommandTextWithRedirect(commandLine, 0);
    }

    private Response sendCommandTextWithRedirect(String commandLine, int redirects) throws IOException {
        if (redirects >= MAX_REDIRECTS) {
            throw new IOException("Demasiadas redirecciones. El cluster puede estar inestable.");
        }

        logger.info("POST /command -> {}", commandLine);
        Response response = doPost("/command", "text/plain; charset=utf-8", commandLine.getBytes(StandardCharsets.UTF_8));

        // Manejo de redirección si el monitor retorna líder
        if (response.isRedirect() && response.getLeaderHost() != null && response.getLeaderPort() > 0) {
            logger.info("Redirigiendo al líder {}:{}", response.getLeaderHost(), response.getLeaderPort());
            this.currentHost = response.getLeaderHost();
            this.currentPort = response.getLeaderPort();
            return sendCommandTextWithRedirect(commandLine, redirects + 1);
        }

        return response;
    }

    /**
     * GET genérico que retorna contenido como String.
     */
    public String getString(String path) throws IOException {
        HttpURLConnection conn = open(path, "GET");
        int code = conn.getResponseCode();
        byte[] body = readBody(conn, code);
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * GET que retorna bytes (para descargas de archivos).
     */
    public byte[] getBytes(String path) throws IOException {
        HttpURLConnection conn = open(path, "GET");
        int code = conn.getResponseCode();
        return readBody(conn, code);
    }

    /**
     * Verifica la conexión consultando /status.
     */
    public boolean testConnection() {
        try {
            String body = getString("/status");
            return body != null && !body.isEmpty();
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

    public String getCurrentHost() { return currentHost; }
    public int getCurrentPort() { return currentPort; }
    public String getCurrentServer() { return currentHost + ":" + currentPort; }

    // ===== Internos HTTP =====

    private HttpURLConnection open(String path, String method) throws IOException {
        URL url = new URL(baseUrl() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setDoInput(true);
        return conn;
    }

    private Response doPost(String path, String contentType, byte[] body) throws IOException {
        HttpURLConnection conn = open(path, "POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", contentType);
        try (OutputStream os = new BufferedOutputStream(conn.getOutputStream())) {
            os.write(body);
        }

        int code = conn.getResponseCode();
        byte[] responseBody = readBody(conn, code);

        String text = new String(responseBody, StandardCharsets.UTF_8).trim();
        Response response = new Response();
        response.setSuccess(code >= 200 && code < 300);
        response.setMessage(text);

        // Intentar parsear JSON estructurado
        try {
            Response parsed = gson.fromJson(text, Response.class);
            if (parsed != null) {
                // Si el servidor devuelve nuestro esquema, úsalo
                response = parsed;
            }
        } catch (JsonSyntaxException ignore) {
            // No era JSON, usamos texto plano en message
        }

        return response;
    }

    private byte[] readBody(HttpURLConnection conn, int code) throws IOException {
        InputStream is;
        try {
            is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) is = conn.getInputStream();
        } catch (IOException e) {
            is = conn.getErrorStream();
            if (is == null) throw e;
        }
        try (BufferedInputStream bis = new BufferedInputStream(is);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = bis.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            return baos.toByteArray();
        }
    }
}

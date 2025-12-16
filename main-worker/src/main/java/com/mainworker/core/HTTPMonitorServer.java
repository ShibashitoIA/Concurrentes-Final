package com.mainworker.core;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.rafthq.core.RaftNode;
import com.rafthq.core.RaftState;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Servidor HTTP para monitorear el estado del Worker
 * Expone endpoints REST para consultar el estado del nodo RAFT, archivos y modelos
 */
public class HTTPMonitorServer {

    private static final Logger LOGGER = Logger.getLogger(HTTPMonitorServer.class.getName());

    private final HttpServer server;
    private final RaftNode raftNode;
    private final WorkerStateMachine stateMachine;
    private final int port;

    public HTTPMonitorServer(int port, RaftNode raftNode, WorkerStateMachine stateMachine) throws IOException {
        this.port = port;
        this.raftNode = raftNode;
        this.stateMachine = stateMachine;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        // Registrar endpoints
        server.createContext("/", new IndexHandler());
        server.createContext("/status", new StatusHandler());
        server.createContext("/files", new FilesHandler());
        server.createContext("/models", new ModelsHandler());
        server.createContext("/health", new HealthHandler());
        server.createContext("/command", new CommandHandler());

        server.setExecutor(null); // Default executor
    }

    public void start() {
        server.start();
        LOGGER.info("HTTP Monitor Server started on port " + port);
        LOGGER.info("Endpoints:");
        LOGGER.info("  - http://localhost:" + port + "/");
        LOGGER.info("  - http://localhost:" + port + "/status");
        LOGGER.info("  - http://localhost:" + port + "/files");
        LOGGER.info("  - http://localhost:" + port + "/models");
        LOGGER.info("  - http://localhost:" + port + "/health");
    }

    public void stop() {
        server.stop(0);
        LOGGER.info("HTTP Monitor Server stopped");
    }

    /**
     * Handler para la página principal
     */
    private class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = buildHTMLPage();
            sendResponse(exchange, 200, response, "text/html");
        }

        private String buildHTMLPage() {
            return "<!DOCTYPE html>" +
                "<html><head><title>Main Worker Monitor</title>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; margin: 40px; background: #f5f5f5; }" +
                "h1 { color: #333; }" +
                "h2 { color: #666; border-bottom: 2px solid #ddd; padding-bottom: 10px; }" +
                ".card { background: white; padding: 20px; margin: 20px 0; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }" +
                "table { border-collapse: collapse; width: 100%; }" +
                "th, td { text-align: left; padding: 12px; border-bottom: 1px solid #ddd; }" +
                "th { background-color: #4CAF50; color: white; }" +
                "a { color: #4CAF50; text-decoration: none; }" +
                "a:hover { text-decoration: underline; }" +
                ".status { padding: 5px 10px; border-radius: 3px; font-weight: bold; }" +
                ".leader { background: #4CAF50; color: white; }" +
                ".follower { background: #2196F3; color: white; }" +
                ".candidate { background: #FF9800; color: white; }" +
                "</style></head><body>" +
                "<h1>Main Worker Monitor</h1>" +
                "<div class='card'>" +
                "<h2>Quick Links</h2>" +
                "<ul>" +
                "<li><a href='/status'>Status (JSON)</a> - Estado del nodo RAFT</li>" +
                "<li><a href='/files'>Files (JSON)</a> - Archivos replicados</li>" +
                "<li><a href='/models'>Models (JSON)</a> - Modelos entrenados</li>" +
                "<li><a href='/health'>Health (JSON)</a> - Estado de salud</li>" +
                "<li><strong>POST /command</strong> - Enviar comandos al cluster (solo si es líder)</li>" +
                "</ul></div>" +
                "<div class='card'>" +
                "<h2>RAFT Status</h2>" +
                "<p><strong>State:</strong> <span class='status " + raftNode.getState().name().toLowerCase() + "'>" +
                raftNode.getState().name() + "</span></p>" +
                "<p><strong>Current Term:</strong> " + raftNode.getCurrentTerm() + "</p>" +
                "<p><strong>Commands Applied:</strong> " + stateMachine.getAppliedCommandsCount() + "</p>" +
                "</div>" +
                "<div class='card'>" +
                "<h2>Storage</h2>" +
                "<p><strong>Total Files:</strong> " + stateMachine.getFileManager().getAllFiles().size() + "</p>" +
                "<p><strong>Total Models:</strong> " + stateMachine.getModelRegistry().getTotalModels() + "</p>" +
                "</div>" +
                "</body></html>";
        }
    }

    /**
     * Handler para /status - Estado del nodo RAFT
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            RaftState state = raftNode.getState();
            int term = raftNode.getCurrentTerm();
            long commandsApplied = stateMachine.getAppliedCommandsCount();

            String json = String.format(
                "{\"state\":\"%s\",\"term\":%d,\"commandsApplied\":%d}",
                state.name(), term, commandsApplied
            );

            sendResponse(exchange, 200, json, "application/json");
        }
    }

    /**
     * Handler para /files - Lista de archivos replicados
     */
    private class FilesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var files = stateMachine.getFileManager().getAllFiles();

            StringBuilder json = new StringBuilder("{\"files\":[");
            boolean first = true;

            for (var entry : files.entrySet()) {
                if (!first) json.append(",");
                json.append(entry.getValue().toJson());
                first = false;
            }

            json.append("],\"count\":").append(files.size()).append("}");

            sendResponse(exchange, 200, json.toString(), "application/json");
        }
    }

    /**
     * Handler para /models - Lista de modelos entrenados
     */
    private class ModelsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var models = stateMachine.getModelRegistry().listAllModels();

            StringBuilder json = new StringBuilder("{\"models\":[");
            boolean first = true;

            for (var model : models) {
                if (!first) json.append(",");
                json.append(model.toJson());
                first = false;
            }

            json.append("],\"count\":").append(models.size()).append("}");

            sendResponse(exchange, 200, json.toString(), "application/json");
        }
    }

    /**
     * Handler para /health - Estado de salud del worker
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String status = "healthy";
            int httpStatus = 200;

            // Verificar que el nodo RAFT esté operativo
            if (raftNode.getState() == null) {
                status = "unhealthy";
                httpStatus = 503;
            }

            String json = String.format(
                "{\"status\":\"%s\",\"timestamp\":%d}",
                status, System.currentTimeMillis()
            );

            sendResponse(exchange, httpStatus, json, "application/json");
        }
    }

    /**
     * Handler para /command - Enviar comandos al cluster (solo al líder)
     * Método: POST
     * Body: comando en texto plano (ej: "NOP" o "REGISTER_MODEL|...")
     */
    private class CommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                String json = "{\"error\":\"Method not allowed. Use POST\"}";
                sendResponse(exchange, 405, json, "application/json");
                return;
            }

            // Verificar que este nodo sea el líder
            if (raftNode.getState() != RaftState.LEADER) {
                String json = String.format(
                    "{\"error\":\"Not leader\",\"state\":\"%s\",\"message\":\"This node is not the leader. Please send commands to the leader.\"}",
                    raftNode.getState().name()
                );
                sendResponse(exchange, 503, json, "application/json");
                return;
            }

            // Leer el comando del body
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {

                String command = reader.readLine();
                if (command == null || command.trim().isEmpty()) {
                    String json = "{\"error\":\"Empty command\"}";
                    sendResponse(exchange, 400, json, "application/json");
                    return;
                }

                command = command.trim();

                // Enviar comando al RaftNode
                byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
                boolean success = raftNode.appendCommand(commandBytes);

                if (success) {
                    String json = String.format(
                        "{\"success\":true,\"command\":\"%s\",\"message\":\"Command appended to log\"}",
                        command.length() > 50 ? command.substring(0, 50) + "..." : command
                    );
                    sendResponse(exchange, 200, json, "application/json");
                } else {
                    String json = "{\"error\":\"Failed to append command\",\"success\":false}";
                    sendResponse(exchange, 500, json, "application/json");
                }

            } catch (Exception e) {
                String json = String.format(
                    "{\"error\":\"Exception: %s\"}",
                    e.getMessage()
                );
                sendResponse(exchange, 500, json, "application/json");
            }
        }
    }

    /**
     * Envía una respuesta HTTP
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*"); // CORS
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

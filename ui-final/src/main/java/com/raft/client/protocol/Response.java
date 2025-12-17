package com.raft.client.protocol;

import java.util.HashMap;
import java.util.Map;

/**
 * Representa la respuesta del servidor RAFT.
 */
public class Response {
    private boolean success;
    private String message;
    private Map<String, Object> data;
    private String errorCode;
    
    // Para redirección al líder
    private boolean redirect;
    private String leaderHost;
    private int leaderPort;
    
    // Datos binarios (archivos, imágenes, etc.) - no se serializa en JSON
    private transient byte[] binaryData;

    public Response() {
        this.data = new HashMap<>();
    }

    public Response(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.data = new HashMap<>();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public void addData(String key, Object value) {
        this.data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public boolean isRedirect() {
        return redirect;
    }

    public void setRedirect(boolean redirect) {
        this.redirect = redirect;
    }

    public String getLeaderHost() {
        return leaderHost;
    }

    public void setLeaderHost(String leaderHost) {
        this.leaderHost = leaderHost;
    }

    public int getLeaderPort() {
        return leaderPort;
    }

    public void setLeaderPort(int leaderPort) {
        this.leaderPort = leaderPort;
    }

    public byte[] getBinaryData() {
        return binaryData;
    }

    public void setBinaryData(byte[] binaryData) {
        this.binaryData = binaryData;
    }

    @Override
    public String toString() {
        return "Response{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", redirect=" + redirect +
                ", leaderHost='" + leaderHost + '\'' +
                ", leaderPort=" + leaderPort +
                ", data=" + data +
                '}';
    }
}

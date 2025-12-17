package com.raft.client.protocol;

import java.util.HashMap;
import java.util.Map;

/**
 * Representa un comando que se envía al servidor RAFT.
 */
public class Command {
    private CommandType type;
    private Map<String, String> parameters;
    private byte[] data;  // Para archivos u otros datos binarios

    public Command() {
        this.parameters = new HashMap<>();
    }

    public Command(CommandType type) {
        this.type = type;
        this.parameters = new HashMap<>();
    }

    public CommandType getType() {
        return type;
    }

    public void setType(CommandType type) {
        this.type = type;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public void addParameter(String key, String value) {
        this.parameters.put(key, value);
    }

    public String getParameter(String key) {
        return parameters.get(key);
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "Command{" +
                "type=" + type +
                ", parameters=" + parameters +
                ", dataSize=" + (data != null ? data.length : 0) +
                '}';
    }
}

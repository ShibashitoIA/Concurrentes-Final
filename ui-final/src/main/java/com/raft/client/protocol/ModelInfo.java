package com.raft.client.protocol;

/**
 * Información de un modelo de IA.
 */
public class ModelInfo {
    private String modelId;
    private String name;
    private String status;  // TRAINING, READY, ERROR
    private String createdAt;
    private int inputSize;
    private int outputSize;
    private String trainingFile;
    private String modelType;  // NUMERIC, IMAGE_CNN, IMAGE_MLP
    private int imageWidth;    // Para modelos de imágenes
    private int imageHeight;   // Para modelos de imágenes
    private int channels;      // 1=grayscale, 3=RGB

    public ModelInfo() {
    }

    public ModelInfo(String modelId, String name, String status) {
        this.modelId = modelId;
        this.name = name;
        this.status = status;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public int getInputSize() {
        return inputSize;
    }

    public void setInputSize(int inputSize) {
        this.inputSize = inputSize;
    }

    public int getOutputSize() {
        return outputSize;
    }

    public void setOutputSize(int outputSize) {
        this.outputSize = outputSize;
    }

    public String getTrainingFile() {
        return trainingFile;
    }

    public void setTrainingFile(String trainingFile) {
        this.trainingFile = trainingFile;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(int imageHeight) {
        this.imageHeight = imageHeight;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    @Override
    public String toString() {
        return "ModelInfo{" +
                "modelId='" + modelId + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", type='" + modelType + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}

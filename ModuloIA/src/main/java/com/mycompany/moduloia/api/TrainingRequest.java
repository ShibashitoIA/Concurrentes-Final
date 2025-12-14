package com.mycompany.moduloia.api;

public class TrainingRequest {

    public final InputType inputType;
    public final String datasetPath;

    public final int inputSize;
    public final int outputSize;

    public final int epochs;
    public final double learningRate;
    public final int numThreads;

    public final boolean hasHeader;

    public final int maxVocab;

    // IMAGE params
    public final int imageWidth;
    public final int imageHeight;
    public final boolean grayscale;

    public TrainingRequest(InputType inputType,
                           String datasetPath,
                           int inputSize,
                           int outputSize,
                           int epochs,
                           double learningRate,
                           int numThreads,
                           boolean hasHeader,
                           int maxVocab,
                           int imageWidth,
                           int imageHeight,
                           boolean grayscale) {
        this.inputType = inputType;
        this.datasetPath = datasetPath;
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        this.epochs = epochs;
        this.learningRate = learningRate;
        this.numThreads = numThreads;
        this.hasHeader = hasHeader;
        this.maxVocab = maxVocab;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.grayscale = grayscale;
    }
}

package com.mycompany.moduloia.storage;

import com.mycompany.moduloia.api.InputType;
import com.mycompany.moduloia.mlp.HiddenActivation;
import com.mycompany.moduloia.mlp.OutputActivation;

public class ModelBundle {

    public final String modelId;
    public final InputType inputType;

    public final int featureSize;
    public final int outputSize;

    public final HiddenActivation hiddenActivation;
    public final OutputActivation outputActivation;

    // Para reconstruir el MLP
    public final int[] layerSizes;
    public final double[][][] weights;
    public final double[][] biases;

    // Estado del extractor (TF-IDF vocab/idf, IMAGE w/h/grayscale, TABULAR vacío)
    public final byte[] extractorState;

    // Parámetros extra para reconstruir extractores con facilidad
    public final int tfidfMaxVocab;

    public ModelBundle(String modelId,
                       InputType inputType,
                       int featureSize,
                       int outputSize,
                       HiddenActivation hiddenActivation,
                       OutputActivation outputActivation,
                       int[] layerSizes,
                       double[][][] weights,
                       double[][] biases,
                       byte[] extractorState,
                       int tfidfMaxVocab) {
        this.modelId = modelId;
        this.inputType = inputType;
        this.featureSize = featureSize;
        this.outputSize = outputSize;
        this.hiddenActivation = hiddenActivation;
        this.outputActivation = outputActivation;
        this.layerSizes = layerSizes;
        this.weights = weights;
        this.biases = biases;
        this.extractorState = extractorState;
        this.tfidfMaxVocab = tfidfMaxVocab;
    }
}

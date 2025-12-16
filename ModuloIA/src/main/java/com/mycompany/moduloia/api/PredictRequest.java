package com.mycompany.moduloia.api;

public class PredictRequest {

    public final InputType inputType;
    public final String modelId;

    public final double[] tabularInput;
    public final String textInput;

    // IMAGE
    public final String imagePath;

    public PredictRequest(InputType inputType,
                          String modelId,
                          double[] tabularInput,
                          String textInput,
                          String imagePath) {
        this.inputType = inputType;
        this.modelId = modelId;
        this.tabularInput = tabularInput;
        this.textInput = textInput;
        this.imagePath = imagePath;
    }
}


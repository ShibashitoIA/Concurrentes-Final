package com.mycompany.moduloia.mlp;

public enum OutputActivation {
    SIGMOID,    // binaria (outputSize=1) o multi-label simple
    SOFTMAX,    // multiclase (outputSize=K, y one-hot)
    LINEAR      // regresión
}

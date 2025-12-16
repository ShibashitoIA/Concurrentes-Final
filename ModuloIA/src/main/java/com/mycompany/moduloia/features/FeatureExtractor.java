package com.mycompany.moduloia.features;

import java.util.List;

public interface FeatureExtractor<T> {

    int getFeatureSize();

    List<double[]> fitTransform(List<T> rawInputs);

    double[] transformOne(T rawInput);

    byte[] serializeState();

    void loadState(byte[] state);
}


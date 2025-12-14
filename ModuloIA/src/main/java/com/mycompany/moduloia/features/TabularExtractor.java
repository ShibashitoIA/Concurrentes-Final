package com.mycompany.moduloia.features;

import java.util.ArrayList;
import java.util.List;

public class TabularExtractor implements FeatureExtractor<double[]> {

    private final int featureSize;

    public TabularExtractor(int featureSize) {
        this.featureSize = featureSize;
    }

    @Override
    public int getFeatureSize() {
        return featureSize;
    }

    @Override
    public List<double[]> fitTransform(List<double[]> rawInputs) {
        List<double[]> out = new ArrayList<>(rawInputs.size());
        for (double[] v : rawInputs) {
            if (v.length != featureSize) {
                throw new IllegalArgumentException("TABULAR input size mismatch: " + v.length + " != " + featureSize);
            }
            out.add(v);
        }
        return out;
    }

    @Override
    public double[] transformOne(double[] rawInput) {
        if (rawInput.length != featureSize) {
            throw new IllegalArgumentException("TABULAR input size mismatch: " + rawInput.length + " != " + featureSize);
        }
        return rawInput;
    }

    @Override
    public byte[] serializeState() {
        return new byte[0];
    }

    @Override
    public void loadState(byte[] state) {
        // no-op
    }
}

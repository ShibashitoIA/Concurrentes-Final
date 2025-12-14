package com.mycompany.moduloia.mlp;

public class TrainingSample {

    public final double[] x;
    public final double[] y;

    public TrainingSample(double[] x, double[] y) {
        if (x == null || y == null) {
            throw new IllegalArgumentException("x and y must not be null");
        }
        this.x = x;
        this.y = y;
    }
}

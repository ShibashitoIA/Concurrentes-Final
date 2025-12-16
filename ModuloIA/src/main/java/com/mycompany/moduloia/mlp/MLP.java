package com.mycompany.moduloia.mlp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MLP {

    private final int[] layerSizes;
    private final HiddenActivation hiddenActivation;
    private final OutputActivation outputActivation;

    // weights[l][i][j] : capa l (1..L), neurona i, conexión desde neurona j de capa l-1
    private final double[][][] weights;
    // biases[l][i] : bias de neurona i en capa l (1..L)
    private final double[][] biases;

    public MLP(int[] layerSizes,
               HiddenActivation hiddenActivation,
               OutputActivation outputActivation,
               long seed) {
        if (layerSizes == null || layerSizes.length < 2) {
            throw new IllegalArgumentException("layerSizes must have at least 2 layers (input, output)");
        }
        for (int s : layerSizes) {
            if (s <= 0) {
                throw new IllegalArgumentException("All layer sizes must be > 0");
            }
        }
        this.layerSizes = layerSizes.clone();
        this.hiddenActivation = hiddenActivation;
        this.outputActivation = outputActivation;

        int L = layerSizes.length - 1;

        this.weights = new double[layerSizes.length][][];
        this.biases = new double[layerSizes.length][];

        Random rnd = new Random(seed);
        for (int l = 1; l <= L; l++) {
            int fanIn = layerSizes[l - 1];
            int fanOut = layerSizes[l];

            weights[l] = new double[fanOut][fanIn];
            biases[l] = new double[fanOut];

            // Xavier/Glorot uniform init
            double limit = Math.sqrt(6.0 / (fanIn + fanOut));
            for (int i = 0; i < fanOut; i++) {
                for (int j = 0; j < fanIn; j++) {
                    weights[l][i][j] = uniform(rnd, -limit, limit);
                }
                biases[l][i] = 0.0;
            }
        }
    }

    public int getInputSize() {
        return layerSizes[0];
    }

    public int getOutputSize() {
        return layerSizes[layerSizes.length - 1];
    }

    public double[] predict(double[] input) {
        ForwardCache cache = forwardWithCache(input);
        return cache.a[cache.a.length - 1];
    }

    public void train(List<TrainingSample> data,
                      int epochs,
                      double learningRate,
                      int batchSize,
                      boolean shuffle,
                      long seedForShuffle) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("data must not be null/empty");
        }
        if (epochs <= 0) {
            throw new IllegalArgumentException("epochs must be > 0");
        }
        if (learningRate <= 0.0) {
            throw new IllegalArgumentException("learningRate must be > 0");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }

        validateSamples(data);

        List<TrainingSample> work = new ArrayList<>(data);
        Random rnd = new Random(seedForShuffle);

        int n = work.size();
        int L = layerSizes.length - 1;

        for (int epoch = 1; epoch <= epochs; epoch++) {
            if (shuffle) {
                Collections.shuffle(work, rnd);
            }

            for (int start = 0; start < n; start += batchSize) {
                int end = Math.min(start + batchSize, n);
                int bsz = end - start;

                double[][][] gradW = new double[layerSizes.length][][];
                double[][] gradB = new double[layerSizes.length][];

                for (int l = 1; l <= L; l++) {
                    gradW[l] = new double[layerSizes[l]][layerSizes[l - 1]];
                    gradB[l] = new double[layerSizes[l]];
                }

                for (int idx = start; idx < end; idx++) {
                    TrainingSample s = work.get(idx);

                    ForwardCache cache = forwardWithCache(s.x);
                    BackpropGrads bg = backprop(cache, s.y);

                    for (int l = 1; l <= L; l++) {
                        for (int i = 0; i < layerSizes[l]; i++) {
                            gradB[l][i] += bg.gradB[l][i];
                            for (int j = 0; j < layerSizes[l - 1]; j++) {
                                gradW[l][i][j] += bg.gradW[l][i][j];
                            }
                        }
                    }
                }

                // Update (promedio por batch)
                double scale = 1.0 / (double) bsz;
                for (int l = 1; l <= L; l++) {
                    for (int i = 0; i < layerSizes[l]; i++) {
                        biases[l][i] -= learningRate * (gradB[l][i] * scale);
                        for (int j = 0; j < layerSizes[l - 1]; j++) {
                            weights[l][i][j] -= learningRate * (gradW[l][i][j] * scale);
                        }
                    }
                }
            }
        }
    }

    public double computeAverageLoss(List<TrainingSample> data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("data must not be null/empty");
        }
        validateSamples(data);

        double sum = 0.0;
        for (TrainingSample s : data) {
            double[] yhat = predict(s.x);
            sum += loss(yhat, s.y);
        }
        return sum / (double) data.size();
    }

    // ----------------- Internals -----------------

    private void validateSamples(List<TrainingSample> data) {
        int in = getInputSize();
        int out = getOutputSize();
        for (TrainingSample s : data) {
            if (s.x.length != in) {
                throw new IllegalArgumentException("Sample input size mismatch: " + s.x.length + " != " + in);
            }
            if (s.y.length != out) {
                throw new IllegalArgumentException("Sample target size mismatch: " + s.y.length + " != " + out);
            }
        }

        // Validaciones suaves según modo
        if (outputActivation == OutputActivation.SIGMOID && out != 1) {
            // permitido para multi-label, pero normalmente binaria es out=1
        }
        if (outputActivation == OutputActivation.SOFTMAX) {
            if (out < 2) {
                throw new IllegalArgumentException("SOFTMAX requires outputSize >= 2");
            }
        }
    }

    private ForwardCache forwardWithCache(double[] input) {
        int L = layerSizes.length - 1;

        double[][] a = new double[layerSizes.length][];
        double[][] z = new double[layerSizes.length][];

        a[0] = input.clone();
        z[0] = null;

        for (int l = 1; l <= L; l++) {
            int curr = layerSizes[l];
            int prev = layerSizes[l - 1];

            double[] zl = new double[curr];
            for (int i = 0; i < curr; i++) {
                double sum = biases[l][i];
                for (int j = 0; j < prev; j++) {
                    sum += weights[l][i][j] * a[l - 1][j];
                }
                zl[i] = sum;
            }
            z[l] = zl;

            double[] al;
            boolean isLast = (l == L);
            if (isLast) {
                al = applyOutputActivation(zl);
            } else {
                al = applyHiddenActivation(zl);
            }
            a[l] = al;
        }

        return new ForwardCache(a, z);
    }

    private BackpropGrads backprop(ForwardCache cache, double[] target) {
        int L = layerSizes.length - 1;

        double[][][] gradW = new double[layerSizes.length][][];
        double[][] gradB = new double[layerSizes.length][];

        for (int l = 1; l <= L; l++) {
            gradW[l] = new double[layerSizes[l]][layerSizes[l - 1]];
            gradB[l] = new double[layerSizes[l]];
        }

        // delta en capa final (propiedad clave):
        // - softmax + crossentropy => delta = yhat - y
        // - sigmoid + binary crossentropy => delta = yhat - y
        // - linear + mse => delta = (yhat - y)
        double[] yhat = cache.a[L];
        double[] delta = new double[layerSizes[L]];
        for (int i = 0; i < delta.length; i++) {
            delta[i] = yhat[i] - target[i];
        }

        // Gradientes para capa L
        for (int i = 0; i < layerSizes[L]; i++) {
            gradB[L][i] = delta[i];
            for (int j = 0; j < layerSizes[L - 1]; j++) {
                gradW[L][i][j] = delta[i] * cache.a[L - 1][j];
            }
        }

        // Backprop a capas ocultas
        for (int l = L - 1; l >= 1; l--) {
            double[] newDelta = new double[layerSizes[l]];

            for (int j = 0; j < layerSizes[l]; j++) {
                double sum = 0.0;
                for (int i = 0; i < layerSizes[l + 1]; i++) {
                    sum += weights[l + 1][i][j] * delta[i];
                }
                // derivada de activación oculta
                double deriv = hiddenActivationDerivative(cache.z[l][j], cache.a[l][j]);
                newDelta[j] = sum * deriv;
            }

            delta = newDelta;

            for (int i = 0; i < layerSizes[l]; i++) {
                gradB[l][i] = delta[i];
                for (int j = 0; j < layerSizes[l - 1]; j++) {
                    gradW[l][i][j] = delta[i] * cache.a[l - 1][j];
                }
            }
        }

        return new BackpropGrads(gradW, gradB);
    }

    private double[] applyHiddenActivation(double[] z) {
        double[] a = new double[z.length];
        if (hiddenActivation == HiddenActivation.RELU) {
            for (int i = 0; i < z.length; i++) {
                a[i] = Math.max(0.0, z[i]);
            }
            return a;
        }
        if (hiddenActivation == HiddenActivation.TANH) {
            for (int i = 0; i < z.length; i++) {
                a[i] = Math.tanh(z[i]);
            }
            return a;
        }
        throw new IllegalStateException("Unknown hidden activation: " + hiddenActivation);
    }

    private double hiddenActivationDerivative(double z, double a) {
        if (hiddenActivation == HiddenActivation.RELU) {
            return (z > 0.0) ? 1.0 : 0.0;
        }
        if (hiddenActivation == HiddenActivation.TANH) {
            // d/dz tanh(z) = 1 - tanh(z)^2 = 1 - a^2
            return 1.0 - (a * a);
        }
        throw new IllegalStateException("Unknown hidden activation: " + hiddenActivation);
    }

    private double[] applyOutputActivation(double[] z) {
        if (outputActivation == OutputActivation.LINEAR) {
            return z.clone();
        }
        if (outputActivation == OutputActivation.SIGMOID) {
            double[] a = new double[z.length];
            for (int i = 0; i < z.length; i++) {
                a[i] = sigmoid(z[i]);
            }
            return a;
        }
        if (outputActivation == OutputActivation.SOFTMAX) {
            return softmax(z);
        }
        throw new IllegalStateException("Unknown output activation: " + outputActivation);
    }

    private double loss(double[] yhat, double[] y) {
        if (outputActivation == OutputActivation.LINEAR) {
            // MSE
            double s = 0.0;
            for (int i = 0; i < yhat.length; i++) {
                double d = yhat[i] - y[i];
                s += d * d;
            }
            return 0.5 * s;
        }

        if (outputActivation == OutputActivation.SIGMOID) {
            // Binary cross entropy (si yhat len==1 típico). Para len>1 lo trata como suma multi-label.
            double eps = 1e-12;
            double s = 0.0;
            for (int i = 0; i < yhat.length; i++) {
                double p = clamp(yhat[i], eps, 1.0 - eps);
                s += -(y[i] * Math.log(p) + (1.0 - y[i]) * Math.log(1.0 - p));
            }
            return s;
        }

        if (outputActivation == OutputActivation.SOFTMAX) {
            // Cross entropy con one-hot
            double eps = 1e-12;
            double s = 0.0;
            for (int i = 0; i < yhat.length; i++) {
                if (y[i] > 0.0) {
                    double p = clamp(yhat[i], eps, 1.0);
                    s += -Math.log(p);
                }
            }
            return s;
        }

        throw new IllegalStateException("Unknown output activation: " + outputActivation);
    }

    private double[] softmax(double[] z) {
        double max = z[0];
        for (int i = 1; i < z.length; i++) {
            if (z[i] > max) {
                max = z[i];
            }
        }

        double sum = 0.0;
        double[] exp = new double[z.length];
        for (int i = 0; i < z.length; i++) {
            exp[i] = Math.exp(z[i] - max);
            sum += exp[i];
        }

        double[] out = new double[z.length];
        if (sum == 0.0) {
            // extremadamente raro, pero por seguridad
            double v = 1.0 / (double) z.length;
            for (int i = 0; i < out.length; i++) {
                out[i] = v;
            }
            return out;
        }

        for (int i = 0; i < z.length; i++) {
            out[i] = exp[i] / sum;
        }
        return out;
    }

    private double sigmoid(double x) {
        // estable
        if (x >= 0.0) {
            double e = Math.exp(-x);
            return 1.0 / (1.0 + e);
        } else {
            double e = Math.exp(x);
            return e / (1.0 + e);
        }
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private double uniform(Random rnd, double lo, double hi) {
        return lo + (hi - lo) * rnd.nextDouble();
    }

    private static class ForwardCache {
        final double[][] a; // activaciones por capa
        final double[][] z; // pre-activaciones por capa

        ForwardCache(double[][] a, double[][] z) {
            this.a = a;
            this.z = z;
        }
    }

    private static class BackpropGrads {
        final double[][][] gradW;
        final double[][] gradB;

        BackpropGrads(double[][][] gradW, double[][] gradB) {
            this.gradW = gradW;
            this.gradB = gradB;
        }
    }
    
        public int[] getLayerSizes() {
        return layerSizes.clone();
    }

    public HiddenActivation getHiddenActivation() {
        return hiddenActivation;
    }

    public OutputActivation getOutputActivation() {
        return outputActivation;
    }

    public double[][][] exportWeightsCopy() {
        int L = layerSizes.length - 1;
        double[][][] out = new double[layerSizes.length][][];
        for (int l = 1; l <= L; l++) {
            out[l] = new double[layerSizes[l]][layerSizes[l - 1]];
            for (int i = 0; i < layerSizes[l]; i++) {
                System.arraycopy(weights[l][i], 0, out[l][i], 0, layerSizes[l - 1]);
            }
        }
        return out;
    }

    public double[][] exportBiasesCopy() {
        int L = layerSizes.length - 1;
        double[][] out = new double[layerSizes.length][];
        for (int l = 1; l <= L; l++) {
            out[l] = new double[layerSizes[l]];
            System.arraycopy(biases[l], 0, out[l], 0, layerSizes[l]);
        }
        return out;
    }

    public void importParameters(double[][][] newWeights, double[][] newBiases) {
        int L = layerSizes.length - 1;

        for (int l = 1; l <= L; l++) {
            if (newWeights[l].length != layerSizes[l]) {
                throw new IllegalArgumentException("weights layer " + l + " rows mismatch");
            }
            if (newWeights[l][0].length != layerSizes[l - 1]) {
                throw new IllegalArgumentException("weights layer " + l + " cols mismatch");
            }
            if (newBiases[l].length != layerSizes[l]) {
                throw new IllegalArgumentException("biases layer " + l + " mismatch");
            }

            for (int i = 0; i < layerSizes[l]; i++) {
                System.arraycopy(newWeights[l][i], 0, weights[l][i], 0, layerSizes[l - 1]);
            }
            System.arraycopy(newBiases[l], 0, biases[l], 0, layerSizes[l]);
        }
    }
}

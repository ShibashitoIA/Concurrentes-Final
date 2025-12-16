package com.mycompany.moduloia.mlp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ParallelMLPTrainer {

    public static void trainDataParallel(MLP globalModel,
                                         List<TrainingSample> data,
                                         int epochs,
                                         double learningRate,
                                         int batchSize,
                                         boolean shuffle,
                                         long seedForShuffle,
                                         int requestedThreads) {
        if (globalModel == null) {
            throw new IllegalArgumentException("globalModel must not be null");
        }
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

        int n = data.size();
        int numThreads = Math.max(1, Math.min(requestedThreads, n));

        if (numThreads == 1) {
            globalModel.train(data, epochs, learningRate, batchSize, shuffle, seedForShuffle);
            return;
        }

        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        try {
            List<TrainingSample> work = new ArrayList<>(data);
            Random rnd = new Random(seedForShuffle);

            for (int epoch = 0; epoch < epochs; epoch++) {
                if (shuffle) {
                    Collections.shuffle(work, rnd);
                }

                List<List<TrainingSample>> parts = split(work, numThreads);

                // Snapshot de parámetros globales al inicio del epoch
                double[][][] baseW = globalModel.exportWeightsCopy();
                double[][] baseB = globalModel.exportBiasesCopy();

                int[] layerSizes = globalModel.getLayerSizes();
                HiddenActivation hidAct = globalModel.getHiddenActivation();
                OutputActivation outAct = globalModel.getOutputActivation();

                List<Future<ReplicaResult>> futures = new ArrayList<>();

                for (int t = 0; t < parts.size(); t++) {
                    final List<TrainingSample> chunk = parts.get(t);
                    final long localSeed = seedForShuffle + 1000L * epoch + t;

                    Callable<ReplicaResult> task = () -> {
                        // Crea réplica y carga parámetros globales
                        MLP local = new MLP(layerSizes, hidAct, outAct, 1L);
                        local.importParameters(baseW, baseB);

                        // Entrena 1 epoch sobre su partición
                        int localBatch = Math.min(batchSize, Math.max(1, chunk.size()));
                        local.train(chunk, 1, learningRate, localBatch, false, localSeed);

                        return new ReplicaResult(local.exportWeightsCopy(), local.exportBiasesCopy());
                    };

                    futures.add(pool.submit(task));
                }

                // Recoger parámetros entrenados
                List<ReplicaResult> results = new ArrayList<>(futures.size());
                for (Future<ReplicaResult> f : futures) {
                    try {
                        results.add(f.get());
                    } catch (ExecutionException ex) {
                        Throwable cause = ex.getCause();
                        if (cause instanceof RuntimeException) {
                            throw (RuntimeException) cause;
                        }
                        throw new RuntimeException("Parallel training failed", cause);
                    }
                }

                // Promedio (model averaging) -> actualizar modelo global
                AveragedParams avg = averageParams(results, layerSizes);
                globalModel.importParameters(avg.weights, avg.biases);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Parallel training interrupted", ie);
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<List<TrainingSample>> split(List<TrainingSample> data, int parts) {
        List<List<TrainingSample>> out = new ArrayList<>(parts);

        int n = data.size();
        int base = n / parts;
        int rem = n % parts;

        int start = 0;
        for (int i = 0; i < parts; i++) {
            int size = base + (i < rem ? 1 : 0);
            int end = start + size;

            if (size <= 0) {
                break;
            }

            out.add(data.subList(start, end));
            start = end;
        }
        return out;
    }

    private static AveragedParams averageParams(List<ReplicaResult> reps, int[] layerSizes) {
        int replicas = reps.size();
        int L = layerSizes.length - 1;

        double[][][] avgW = new double[layerSizes.length][][];
        double[][] avgB = new double[layerSizes.length][];

        for (int l = 1; l <= L; l++) {
            avgW[l] = new double[layerSizes[l]][layerSizes[l - 1]];
            avgB[l] = new double[layerSizes[l]];
        }

        // Sumar
        for (ReplicaResult r : reps) {
            for (int l = 1; l <= L; l++) {
                for (int i = 0; i < layerSizes[l]; i++) {
                    avgB[l][i] += r.biases[l][i];
                    for (int j = 0; j < layerSizes[l - 1]; j++) {
                        avgW[l][i][j] += r.weights[l][i][j];
                    }
                }
            }
        }

        // Promediar
        double inv = 1.0 / (double) replicas;
        for (int l = 1; l <= L; l++) {
            for (int i = 0; i < layerSizes[l]; i++) {
                avgB[l][i] *= inv;
                for (int j = 0; j < layerSizes[l - 1]; j++) {
                    avgW[l][i][j] *= inv;
                }
            }
        }

        return new AveragedParams(avgW, avgB);
    }

    private static class ReplicaResult {
        final double[][][] weights;
        final double[][] biases;

        ReplicaResult(double[][][] weights, double[][] biases) {
            this.weights = weights;
            this.biases = biases;
        }
    }

    private static class AveragedParams {
        final double[][][] weights;
        final double[][] biases;

        AveragedParams(double[][][] weights, double[][] biases) {
            this.weights = weights;
            this.biases = biases;
        }
    }
}

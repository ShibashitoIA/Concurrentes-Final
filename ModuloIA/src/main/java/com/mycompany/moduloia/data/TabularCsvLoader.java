package com.mycompany.moduloia.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class TabularCsvLoader {

    public TabularDataset load(String path, boolean hasHeader, int inputSize, int outputSize) {
        if (inputSize <= 0) {
            throw new IllegalArgumentException("inputSize must be > 0");
        }
        if (outputSize <= 0) {
            throw new IllegalArgumentException("outputSize must be > 0");
        }

        List<double[]> xList = new ArrayList<>();
        List<double[]> yList = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            int lineNo = 0;
            boolean skippedHeader = false;

            while ((line = br.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (hasHeader && !skippedHeader) {
                    skippedHeader = true;
                    continue;
                }

                String[] parts = splitCsvSimple(line);
                int cols = parts.length;

                if (cols == inputSize + outputSize) {
                    double[] x = new double[inputSize];
                    double[] y = new double[outputSize];

                    for (int i = 0; i < inputSize; i++) {
                        x[i] = parseDouble(parts[i], lineNo, i);
                    }
                    for (int j = 0; j < outputSize; j++) {
                        y[j] = parseDouble(parts[inputSize + j], lineNo, inputSize + j);
                    }

                    xList.add(x);
                    yList.add(y);
                    continue;
                }

                if (outputSize > 1 && cols == inputSize + 1) {
                    // Última columna = classIndex (0..K-1)
                    double[] x = new double[inputSize];
                    for (int i = 0; i < inputSize; i++) {
                        x[i] = parseDouble(parts[i], lineNo, i);
                    }

                    int classIndex = parseInt(parts[inputSize], lineNo, inputSize);
                    if (classIndex < 0 || classIndex >= outputSize) {
                        throw new IllegalArgumentException("Invalid classIndex at line " + lineNo
                                + ": " + classIndex + " (expected 0.." + (outputSize - 1) + ")");
                    }

                    double[] y = new double[outputSize];
                    y[classIndex] = 1.0;

                    xList.add(x);
                    yList.add(y);
                    continue;
                }

                throw new IllegalArgumentException("Invalid column count at line " + lineNo
                        + ": got " + cols
                        + ", expected " + (inputSize + outputSize)
                        + " (full targets) OR " + (inputSize + 1)
                        + " (class index when outputSize>1)");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load TABULAR CSV: " + path, e);
        }

        if (xList.isEmpty()) {
            throw new IllegalArgumentException("Dataset is empty: " + path);
        }

        return new TabularDataset(xList, yList);
    }

    private String[] splitCsvSimple(String line) {
        // CSV numérico simple: no soporta comillas (a propósito).
        // Para TABULAR suele ser suficiente (solo números).
        String[] raw = line.split(",", -1);
        for (int i = 0; i < raw.length; i++) {
            raw[i] = raw[i].trim();
        }
        return raw;
    }

    private double parseDouble(String s, int lineNo, int colNo) {
        try {
            if (s == null || s.isEmpty()) {
                throw new NumberFormatException("empty");
            }
            return Double.parseDouble(s);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid number at line " + lineNo
                    + ", col " + (colNo + 1) + ": '" + s + "'");
        }
    }

    private int parseInt(String s, int lineNo, int colNo) {
        try {
            if (s == null || s.isEmpty()) {
                throw new NumberFormatException("empty");
            }
            return Integer.parseInt(s.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid integer at line " + lineNo
                    + ", col " + (colNo + 1) + ": '" + s + "'");
        }
    }
}

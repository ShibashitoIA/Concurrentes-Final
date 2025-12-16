package com.mycompany.moduloia.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class TextTsvLoader {

    public TextDataset load(String path, boolean hasHeader, int outputSize) {
        if (outputSize <= 0) {
            throw new IllegalArgumentException("outputSize must be > 0");
        }

        List<String> texts = new ArrayList<>();
        List<double[]> yList = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            int lineNo = 0;
            boolean skippedHeader = false;

            while ((line = br.readLine()) != null) {
                lineNo++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                if (hasHeader && !skippedHeader) {
                    skippedHeader = true;
                    continue;
                }

                int sep = line.lastIndexOf('\t');
                if (sep < 0) {
                    throw new IllegalArgumentException("Invalid TSV at line " + lineNo
                            + ": missing tab separator (expected 'text<TAB>label')");
                }

                String text = line.substring(0, sep).trim();
                String labelStr = line.substring(sep + 1).trim();

                if (text.isEmpty()) {
                    throw new IllegalArgumentException("Empty text at line " + lineNo);
                }
                if (labelStr.isEmpty()) {
                    throw new IllegalArgumentException("Empty label at line " + lineNo);
                }

                double[] y = parseLabel(labelStr, outputSize, lineNo);

                texts.add(text);
                yList.add(y);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load TFIDF TSV: " + path, e);
        }

        if (texts.isEmpty()) {
            throw new IllegalArgumentException("Dataset is empty: " + path);
        }

        return new TextDataset(texts, yList);
    }

    private double[] parseLabel(String labelStr, int outputSize, int lineNo) {
        if (outputSize == 1) {
            double v = parseDouble(labelStr, lineNo);
            return new double[]{v};
        }

        int classIndex = parseInt(labelStr, lineNo);
        if (classIndex < 0 || classIndex >= outputSize) {
            throw new IllegalArgumentException("Invalid classIndex at line " + lineNo
                    + ": " + classIndex + " (expected 0.." + (outputSize - 1) + ")");
        }

        double[] y = new double[outputSize];
        y[classIndex] = 1.0;
        return y;
    }

    private double parseDouble(String s, int lineNo) {
        try {
            return Double.parseDouble(s);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid numeric label at line " + lineNo + ": '" + s + "'");
        }
    }

    private int parseInt(String s, int lineNo) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid integer label at line " + lineNo + ": '" + s + "'");
        }
    }
}

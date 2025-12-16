package com.mycompany.moduloia.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ImageTsvLoader {

    public ImagePathDataset load(String tsvPath, boolean hasHeader, int outputSize) {
        if (outputSize <= 0) {
            throw new IllegalArgumentException("outputSize must be > 0");
        }

        Path tsvFile = Paths.get(tsvPath).toAbsolutePath().normalize();
        Path baseDir = tsvFile.getParent();
        if (baseDir == null) {
            baseDir = Paths.get(".").toAbsolutePath().normalize();
        }

        List<String> paths = new ArrayList<>();
        List<double[]> yList = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(tsvFile.toFile()))) {
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
                            + ": missing tab separator (expected 'path<TAB>label')");
                }

                String pathStr = line.substring(0, sep).trim();
                String labelStr = line.substring(sep + 1).trim();

                if (pathStr.isEmpty()) {
                    throw new IllegalArgumentException("Empty image path at line " + lineNo);
                }
                if (labelStr.isEmpty()) {
                    throw new IllegalArgumentException("Empty label at line " + lineNo);
                }

                Path p = Paths.get(pathStr);
                if (!p.isAbsolute()) {
                    p = baseDir.resolve(p).normalize();
                }

                if (!Files.exists(p)) {
                    throw new IllegalArgumentException("Image file does not exist at line " + lineNo + ": " + p);
                }
                if (!Files.isRegularFile(p)) {
                    throw new IllegalArgumentException("Image path is not a file at line " + lineNo + ": " + p);
                }

                double[] y = parseLabel(labelStr, outputSize, lineNo);

                paths.add(p.toString());
                yList.add(y);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load IMAGE TSV: " + tsvFile, e);
        }

        if (paths.isEmpty()) {
            throw new IllegalArgumentException("Dataset is empty: " + tsvFile);
        }

        return new ImagePathDataset(paths, yList);
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

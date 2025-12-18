package com.mycompany.moduloia.features;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ImageExtractor implements FeatureExtractor<String> {

    private int width;
    private int height;
    private boolean grayscale;

    public ImageExtractor(int width, int height, boolean grayscale) {
        this.width = width;
        this.height = height;
        this.grayscale = grayscale;
    }

    @Override
    public int getFeatureSize() {
        int channels = grayscale ? 1 : 3;
        return width * height * channels;
    }

    @Override
    public List<double[]> fitTransform(List<String> rawInputs) {
        // Para imágenes no se “fitea” nada: solo se transforma.
        List<double[]> out = new ArrayList<>(rawInputs.size());
        for (String path : rawInputs) {
            out.add(transformOne(path));
        }
        return out;
    }

    @Override
    public double[] transformOne(String rawInput) {
        return ImagePreprocessor.preprocess(
                Paths.get(rawInput),
                width,
                height,
                grayscale,
                true
        );
    }
    
    /**
     * Transforma bytes de imagen directamente (para predicción)
     */
    public double[] transformFromBytes(byte[] imageBytes) {
        return ImagePreprocessor.preprocessFromBytes(
                imageBytes,
                width,
                height,
                grayscale,
                true
        );
    }

    @Override
    public byte[] serializeState() {
        // Guardamos width, height, grayscale para predicción consistente
        return new byte[]{
                (byte) (width >> 24), (byte) (width >> 16), (byte) (width >> 8), (byte) (width),
                (byte) (height >> 24), (byte) (height >> 16), (byte) (height >> 8), (byte) (height),
                (byte) (grayscale ? 1 : 0)
        };
    }

    @Override
    public void loadState(byte[] state) {
        if (state == null || state.length < 9) {
            throw new IllegalArgumentException("Invalid ImageExtractor state");
        }
        width = ((state[0] & 0xFF) << 24) | ((state[1] & 0xFF) << 16) | ((state[2] & 0xFF) << 8) | (state[3] & 0xFF);
        height = ((state[4] & 0xFF) << 24) | ((state[5] & 0xFF) << 16) | ((state[6] & 0xFF) << 8) | (state[7] & 0xFF);
        grayscale = state[8] != 0;
    }
}

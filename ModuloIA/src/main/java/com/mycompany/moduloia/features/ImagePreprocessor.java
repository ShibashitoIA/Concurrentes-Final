package com.mycompany.moduloia.features;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

public class ImagePreprocessor {

    public static double[] preprocess(Path imagePath,
                                      int targetWidth,
                                      int targetHeight,
                                      boolean grayscale,
                                      boolean normalizeToUnit) {
        try {
            BufferedImage original = ImageIO.read(imagePath.toFile());
            if (original == null) {
                throw new IllegalArgumentException("Unsupported or unreadable image: " + imagePath);
            }

            BufferedImage resized = resize(original, targetWidth, targetHeight);

            if (grayscale) {
                return toGrayscaleVector(resized, normalizeToUnit);
            }
            return toRgbVector(resized, normalizeToUnit);
        } catch (Exception e) {
            throw new RuntimeException("Failed to preprocess image: " + imagePath, e);
        }
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = out.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(src, 0, 0, w, h, null);
        } finally {
            g2d.dispose();
        }
        return out;
    }

    private static double[] toGrayscaleVector(BufferedImage img, boolean normalizeToUnit) {
        int w = img.getWidth();
        int h = img.getHeight();
        double[] vec = new double[w * h];

        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                double gray = 0.299 * r + 0.587 * g + 0.114 * b;
                if (normalizeToUnit) {
                    gray = gray / 255.0;
                }
                vec[idx++] = gray;
            }
        }
        return vec;
    }

    private static double[] toRgbVector(BufferedImage img, boolean normalizeToUnit) {
        int w = img.getWidth();
        int h = img.getHeight();
        double[] vec = new double[w * h * 3];

        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                double r = (rgb >> 16) & 0xFF;
                double g = (rgb >> 8) & 0xFF;
                double b = rgb & 0xFF;

                if (normalizeToUnit) {
                    r /= 255.0;
                    g /= 255.0;
                    b /= 255.0;
                }

                vec[idx++] = r;
                vec[idx++] = g;
                vec[idx++] = b;
            }
        }
        return vec;
    }
}

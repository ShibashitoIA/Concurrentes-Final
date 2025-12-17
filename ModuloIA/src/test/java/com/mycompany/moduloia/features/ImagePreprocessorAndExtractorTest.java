package com.mycompany.moduloia.features;

import org.junit.Assert;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImagePreprocessorAndExtractorTest {

    @Test
    public void testPreprocessGrayscaleVectorSizeAndRange() throws Exception {
        Path tmpDir = Files.createTempDirectory("imgtest");
        File imgFile = tmpDir.resolve("test.png").toFile();

        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        // patrón simple (gradiente)
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                int v = (x + y) * 10;
                int rgb = (v << 16) | (v << 8) | v;
                img.setRGB(x, y, rgb);
            }
        }
        ImageIO.write(img, "png", imgFile);

        double[] vec = ImagePreprocessor.preprocess(
                imgFile.toPath(),
                32,
                32,
                true,
                true
        );

        Assert.assertEquals(32 * 32, vec.length);

        for (double d : vec) {
            Assert.assertTrue(d >= 0.0);
            Assert.assertTrue(d <= 1.0);
        }
    }

    @Test
    public void testImageExtractorSerializeLoadState() throws Exception {
        ImageExtractor ex1 = new ImageExtractor(32, 32, true);
        byte[] st = ex1.serializeState();

        ImageExtractor ex2 = new ImageExtractor(1, 1, false);
        ex2.loadState(st);

        Assert.assertEquals(ex1.getFeatureSize(), ex2.getFeatureSize());
        Assert.assertEquals(32 * 32, ex2.getFeatureSize());
    }
}

package com.mycompany.moduloia.storage;

import com.mycompany.moduloia.api.InputType;
import com.mycompany.moduloia.mlp.HiddenActivation;
import com.mycompany.moduloia.mlp.MLP;
import com.mycompany.moduloia.mlp.OutputActivation;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class ModelSerializerRoundTripTest {

    @Test
    public void testSaveLoadKeepsPrediction() throws Exception {
        int[] layers = new int[]{2, 3, 1};

        MLP mlp = new MLP(layers, HiddenActivation.RELU, OutputActivation.SIGMOID, 42L);

        ModelBundle bundle = new ModelBundle(
                "MODEL-TEST",
                InputType.TABULAR,
                2,
                1,
                HiddenActivation.RELU,
                OutputActivation.SIGMOID,
                mlp.getLayerSizes(),
                mlp.exportWeightsCopy(),
                mlp.exportBiasesCopy(),
                new byte[0],
                0
        );

        double[] input = new double[]{0.5, -0.2};
        double[] outBefore = mlp.predict(input);

        Path tmp = Files.createTempFile("model", ".bin");
        new ModelSerializer().save(bundle, tmp);

        ModelBundle loaded = new ModelSerializer().load(tmp);

        Assert.assertEquals(bundle.modelId, loaded.modelId);
        Assert.assertEquals(bundle.inputType, loaded.inputType);
        Assert.assertEquals(bundle.featureSize, loaded.featureSize);
        Assert.assertEquals(bundle.outputSize, loaded.outputSize);

        MLP mlp2 = new MLP(loaded.layerSizes, loaded.hiddenActivation, loaded.outputActivation, 1L);
        mlp2.importParameters(loaded.weights, loaded.biases);

        double[] outAfter = mlp2.predict(input);

        Assert.assertEquals(outBefore.length, outAfter.length);
        for (int i = 0; i < outBefore.length; i++) {
            Assert.assertEquals(outBefore[i], outAfter[i], 1e-12);
        }
    }
}

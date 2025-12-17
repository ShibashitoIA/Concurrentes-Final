package com.mycompany.moduloia.mlp;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParallelMLPTrainerTest {

    @Test
    public void testParallelTrainingReducesLoss() {
        List<TrainingSample> data = makeLinearlySeparableDataset(80, 123L);

        int[] layers = new int[]{2, 16, 1};
        MLP mlp = new MLP(layers, HiddenActivation.RELU, OutputActivation.SIGMOID, 7L);

        double lossBefore = mlp.computeAverageLoss(data);

        ParallelMLPTrainer.trainDataParallel(
                mlp,
                data,
                8,
                0.1,
                16,
                false,
                999L,
                4
        );

        double lossAfter = mlp.computeAverageLoss(data);

        Assert.assertTrue("Expected lossAfter < lossBefore but got " + lossAfter + " >= " + lossBefore,
                lossAfter < lossBefore);
    }

    private List<TrainingSample> makeLinearlySeparableDataset(int n, long seed) {
        Random rnd = new Random(seed);
        List<TrainingSample> out = new ArrayList<>(n);

        // regla: si x1 + x2 > 0 -> 1, else 0
        for (int i = 0; i < n; i++) {
            double x1 = rnd.nextGaussian();
            double x2 = rnd.nextGaussian();
            double y = (x1 + x2 > 0.0) ? 1.0 : 0.0;

            out.add(new TrainingSample(
                    new double[]{x1, x2},
                    new double[]{y}
            ));
        }
        return out;
    }
}

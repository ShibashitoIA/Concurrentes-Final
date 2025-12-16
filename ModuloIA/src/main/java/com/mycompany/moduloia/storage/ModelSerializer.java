package com.mycompany.moduloia.storage;

import com.mycompany.moduloia.api.InputType;
import com.mycompany.moduloia.mlp.HiddenActivation;
import com.mycompany.moduloia.mlp.OutputActivation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;

public class ModelSerializer {

    private static final int FORMAT_VERSION = 1;

    public void save(ModelBundle bundle, Path file) {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(file.toFile()))) {
            dos.writeInt(FORMAT_VERSION);

            dos.writeUTF(bundle.modelId);
            dos.writeInt(bundle.inputType.ordinal());

            dos.writeInt(bundle.featureSize);
            dos.writeInt(bundle.outputSize);

            dos.writeInt(bundle.hiddenActivation.ordinal());
            dos.writeInt(bundle.outputActivation.ordinal());

            // tfidfMaxVocab (si no aplica, puede ser 0)
            dos.writeInt(bundle.tfidfMaxVocab);

            // layerSizes
            dos.writeInt(bundle.layerSizes.length);
            for (int s : bundle.layerSizes) {
                dos.writeInt(s);
            }

            int L = bundle.layerSizes.length - 1;

            // weights
            for (int l = 1; l <= L; l++) {
                int rows = bundle.layerSizes[l];
                int cols = bundle.layerSizes[l - 1];
                for (int i = 0; i < rows; i++) {
                    for (int j = 0; j < cols; j++) {
                        dos.writeDouble(bundle.weights[l][i][j]);
                    }
                }
            }

            // biases
            for (int l = 1; l <= L; l++) {
                int rows = bundle.layerSizes[l];
                for (int i = 0; i < rows; i++) {
                    dos.writeDouble(bundle.biases[l][i]);
                }
            }

            // extractorState
            byte[] st = (bundle.extractorState == null) ? new byte[0] : bundle.extractorState;
            dos.writeInt(st.length);
            dos.write(st);

            dos.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save model: " + file, e);
        }
    }

    public ModelBundle load(Path file) {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file.toFile()))) {
            int ver = dis.readInt();
            if (ver != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported model format version: " + ver);
            }

            String modelId = dis.readUTF();
            InputType inputType = InputType.values()[dis.readInt()];

            int featureSize = dis.readInt();
            int outputSize = dis.readInt();

            HiddenActivation hiddenActivation = HiddenActivation.values()[dis.readInt()];
            OutputActivation outputActivation = OutputActivation.values()[dis.readInt()];

            int tfidfMaxVocab = dis.readInt();

            int nLayers = dis.readInt();
            int[] layerSizes = new int[nLayers];
            for (int i = 0; i < nLayers; i++) {
                layerSizes[i] = dis.readInt();
            }

            int L = layerSizes.length - 1;

            double[][][] weights = new double[layerSizes.length][][];
            double[][] biases = new double[layerSizes.length][];

            for (int l = 1; l <= L; l++) {
                weights[l] = new double[layerSizes[l]][layerSizes[l - 1]];
                biases[l] = new double[layerSizes[l]];
            }

            for (int l = 1; l <= L; l++) {
                int rows = layerSizes[l];
                int cols = layerSizes[l - 1];
                for (int i = 0; i < rows; i++) {
                    for (int j = 0; j < cols; j++) {
                        weights[l][i][j] = dis.readDouble();
                    }
                }
            }

            for (int l = 1; l <= L; l++) {
                int rows = layerSizes[l];
                for (int i = 0; i < rows; i++) {
                    biases[l][i] = dis.readDouble();
                }
            }

            int stLen = dis.readInt();
            byte[] state = new byte[stLen];
            if (stLen > 0) {
                dis.readFully(state);
            }

            return new ModelBundle(
                    modelId,
                    inputType,
                    featureSize,
                    outputSize,
                    hiddenActivation,
                    outputActivation,
                    layerSizes,
                    weights,
                    biases,
                    state,
                    tfidfMaxVocab
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load model: " + file, e);
        }
    }
}


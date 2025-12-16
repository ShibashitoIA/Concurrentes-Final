package com.mycompany.moduloia.api;

import com.mycompany.moduloia.data.ImagePathDataset;
import com.mycompany.moduloia.data.ImageTsvLoader;
import com.mycompany.moduloia.data.TabularCsvLoader;
import com.mycompany.moduloia.data.TabularDataset;
import com.mycompany.moduloia.data.TextDataset;
import com.mycompany.moduloia.data.TextTsvLoader;
import com.mycompany.moduloia.features.ImageExtractor;
import com.mycompany.moduloia.features.TabularExtractor;
import com.mycompany.moduloia.features.TfidfVectorizer;
import com.mycompany.moduloia.mlp.HiddenActivation;
import com.mycompany.moduloia.mlp.MLP;
import com.mycompany.moduloia.mlp.OutputActivation;
import com.mycompany.moduloia.mlp.TrainingSample;
import com.mycompany.moduloia.storage.ModelBundle;
import com.mycompany.moduloia.storage.ModelRegistry;
import com.mycompany.moduloia.storage.ModelSerializer;
import com.mycompany.moduloia.mlp.ParallelMLPTrainer;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AIService {

    private final Path modelsDir;
    private final ModelRegistry registry;
    private final ModelSerializer serializer = new ModelSerializer();

    private final ConcurrentHashMap<String, ModelBundle> cache = new ConcurrentHashMap<>();

    private final int defaultHiddenSize;

    public AIService(Path modelsDir) {
        this(modelsDir, 128);
    }

    public AIService(Path modelsDir, int defaultHiddenSize) {
        try {
            this.modelsDir = modelsDir.toAbsolutePath().normalize();
            Files.createDirectories(this.modelsDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init modelsDir: " + modelsDir, e);
        }
        this.registry = new ModelRegistry(this.modelsDir.resolve("models_index.tsv"));
        this.defaultHiddenSize = defaultHiddenSize;
    }

    public String trainModel(TrainingRequest req) {
        String modelId = UUID.randomUUID().toString();

        List<double[]> x;
        List<double[]> y;
        byte[] extractorState;
        int featureSize;
        int tfidfMaxVocab = 0;

        if (req.inputType == InputType.TABULAR) {
            TabularDataset ds = new TabularCsvLoader().load(req.datasetPath, req.hasHeader, req.inputSize, req.outputSize);
            TabularExtractor ex = new TabularExtractor(req.inputSize);
            x = ex.fitTransform(ds.x);
            y = ds.y;
            extractorState = ex.serializeState();
            featureSize = ex.getFeatureSize();

        } else if (req.inputType == InputType.TFIDF) {
            TextDataset ds = new TextTsvLoader().load(req.datasetPath, req.hasHeader, req.outputSize);
            TfidfVectorizer ex = new TfidfVectorizer(req.maxVocab);
            x = ex.fitTransform(ds.texts);
            y = ds.y;
            extractorState = ex.serializeState();
            featureSize = ex.getFeatureSize();
            tfidfMaxVocab = req.maxVocab;

        } else if (req.inputType == InputType.IMAGE) {
            ImagePathDataset ds = new ImageTsvLoader().load(req.datasetPath, req.hasHeader, req.outputSize);
            ImageExtractor ex = new ImageExtractor(req.imageWidth, req.imageHeight, req.grayscale);
            x = ex.fitTransform(ds.paths);
            y = ds.y;
            extractorState = ex.serializeState();
            featureSize = ex.getFeatureSize();

        } else {
            throw new IllegalArgumentException("Unsupported inputType: " + req.inputType);
        }

        OutputActivation outAct = chooseOutputActivation(req.outputSize, y);
        HiddenActivation hidAct = HiddenActivation.RELU;

        int hiddenSize = computeHiddenSize(featureSize);
        int[] layers = new int[]{featureSize, hiddenSize, req.outputSize};

        MLP mlp = new MLP(layers, hidAct, outAct, 42L);

        List<TrainingSample> samples = new ArrayList<>(x.size());
        for (int i = 0; i < x.size(); i++) {
            samples.add(new TrainingSample(x.get(i), y.get(i)));
        }

        int batchSize = Math.min(32, samples.size());
        ParallelMLPTrainer.trainDataParallel(
                mlp,
                samples,
                req.epochs,
                req.learningRate,
                batchSize,
                true,
                123L,
                req.numThreads
        );


        ModelBundle bundle = new ModelBundle(
                modelId,
                req.inputType,
                featureSize,
                req.outputSize,
                hidAct,
                outAct,
                mlp.getLayerSizes(),
                mlp.exportWeightsCopy(),
                mlp.exportBiasesCopy(),
                extractorState,
                tfidfMaxVocab
        );

        Path file = modelFilePath(modelId);
        serializer.save(bundle, file);

        registry.put(modelId, file.getFileName().toString());
        cache.put(modelId, bundle);

        return modelId;
    }

    public double[] predict(PredictRequest req) {
        ModelBundle bundle = getOrLoad(req.modelId);

        // Mejora: si el cliente manda un inputType que no coincide, fallamos con mensaje claro
        if (req.inputType != null && req.inputType != bundle.inputType) {
            throw new IllegalArgumentException("PredictRequest inputType mismatch: request=" + req.inputType
                    + " but model=" + bundle.inputType);
        }

        double[] features;

        if (bundle.inputType == InputType.TABULAR) {
            if (req.tabularInput == null) {
                throw new IllegalArgumentException("TABULAR predict requires tabularInput");
            }
            TabularExtractor ex = new TabularExtractor(bundle.featureSize);
            features = ex.transformOne(req.tabularInput);

        } else if (bundle.inputType == InputType.TFIDF) {
            if (req.textInput == null) {
                throw new IllegalArgumentException("TFIDF predict requires textInput");
            }
            TfidfVectorizer ex = new TfidfVectorizer(bundle.tfidfMaxVocab);
            ex.loadState(bundle.extractorState);
            features = ex.transformOne(req.textInput);

        } else if (bundle.inputType == InputType.IMAGE) {
            if (req.imagePath == null) {
                throw new IllegalArgumentException("IMAGE predict requires imagePath");
            }
            ImageExtractor ex = new ImageExtractor(1, 1, true);
            ex.loadState(bundle.extractorState);
            features = ex.transformOne(req.imagePath);

        } else {
            throw new IllegalArgumentException("Unsupported inputType: " + bundle.inputType);
        }

        MLP mlp = new MLP(bundle.layerSizes, bundle.hiddenActivation, bundle.outputActivation, 1L);
        mlp.importParameters(bundle.weights, bundle.biases);

        return mlp.predict(features);
    }

    public void saveModel(String modelId, Path directory) {
        ModelBundle bundle = getOrLoad(modelId);
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create directory: " + directory, e);
        }
        serializer.save(bundle, directory.resolve(modelId + ".bin"));
    }

    public void loadModel(String modelId, Path directory) {
        Path dir = directory.toAbsolutePath().normalize();
        Path file = dir.resolve(modelId + ".bin");

        ModelBundle bundle = serializer.load(file);

        // Mejora: validación fuerte del modelId
        if (!bundle.modelId.equals(modelId)) {
            throw new IllegalArgumentException("Loaded modelId mismatch. Expected " + modelId
                    + " but file contains " + bundle.modelId);
        }

        cache.put(modelId, bundle);

        // Si lo estás cargando dentro del modelsDir principal, lo registramos
        if (dir.equals(modelsDir)) {
            registry.put(modelId, file.getFileName().toString());
        }
    }

    private ModelBundle getOrLoad(String modelId) {
        ModelBundle cached = cache.get(modelId);
        if (cached != null) {
            return cached;
        }

        String filename = registry.get(modelId);
        if (filename == null) {
            throw new IllegalArgumentException("Unknown modelId: " + modelId);
        }

        Path file = modelsDir.resolve(filename);
        ModelBundle bundle = serializer.load(file);
        cache.put(modelId, bundle);
        return bundle;
    }

    private Path modelFilePath(String modelId) {
        return modelsDir.resolve(modelId + ".bin");
    }

    private int computeHiddenSize(int featureSize) {
        int hs = Math.min(defaultHiddenSize, Math.max(16, featureSize / 2));
        return hs;
    }

    private OutputActivation chooseOutputActivation(int outputSize, List<double[]> y) {
        if (outputSize > 1) {
            return OutputActivation.SOFTMAX;
        }
        boolean binaryLike = true;
        for (double[] t : y) {
            double v = t[0];
            if (!(almost(v, 0.0) || almost(v, 1.0))) {
                binaryLike = false;
                break;
            }
        }
        return binaryLike ? OutputActivation.SIGMOID : OutputActivation.LINEAR;
    }

    private boolean almost(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }
}

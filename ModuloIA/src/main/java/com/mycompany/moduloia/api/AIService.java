package com.mycompany.moduloia.api;

import com.mycompany.moduloia.features.TabularExtractor;
import com.mycompany.moduloia.features.TfidfVectorizer;

import java.nio.file.Path;
import java.util.UUID;

public class AIService {

    private final Path modelsDir;

    public AIService(Path modelsDir) {
        this.modelsDir = modelsDir;
    }

    public String trainModel(TrainingRequest req) {
        String modelId = UUID.randomUUID().toString();

        // TODO:
        // 1) cargar dataset según req.inputType
        // 2) construir extractor (TabularExtractor o TfidfVectorizer)
        // 3) fitTransform para obtener X
        // 4) entrenar MLP (single o paralelo)
        // 5) persistir modelo + estado extractor
        // 6) actualizar índice modelId -> archivo

        return modelId;
    }

    public double[] predict(PredictRequest req) {
        // TODO:
        // 1) cargar modelo
        // 2) cargar extractor state
        // 3) transformar input (tabular o texto)
        // 4) forward

        return new double[0];
    }

    public void saveModel(String modelId, Path directory) {
        // TODO
    }

    public void loadModel(String modelId, Path directory) {
        // TODO
    }
}

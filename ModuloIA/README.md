# ModuloIA (Java) — Librería de IA (TABULAR / TF-IDF / IMAGEN) + MLP + Entrenamiento paralelo

Este repositorio implementa el **módulo de IA** del proyecto distribuido. Es una **librería Java** que permite:

- Entrenar modelos (MLP) a partir de:
  - **TABULAR** (CSV numérico)
  - **TF-IDF** (texto en TSV → vector TF-IDF)
  - **IMAGEN** (TSV con rutas → preprocesamiento a vector)
- Realizar predicciones sobre un `modelId`
- Persistir modelos en disco y volverlos a cargar
- Entrenamiento **paralelo (data-parallel)** usando `numThreads`

> Este repositorio NO incluye RAFT, sockets, HTTP ni UI. Lo consume el Worker (otro servicio).

---

## Requisitos

- Java **21** (según configuración del proyecto)
- Maven

Compilar:
```bash
mvn clean install
````

Tests (si están agregados):

```bash
mvn test
```

---

## Estructura principal

```
src/main/java/com/mycompany/moduloia/
    api/            InputType, TrainingRequest, PredictRequest, AIService
    data/           Loaders y datasets (tabular/texto/imagen)
    features/       Extractores: TabularExtractor, TfidfVectorizer, ImageExtractor + ImagePreprocessor
    mlp/            MLP + TrainingSample + entrenamiento paralelo (ParallelMLPTrainer)
    storage/        Persistencia binaria: ModelBundle, ModelSerializer, ModelRegistry
```

---

## Concepto de funcionamiento

Todos los inputs se convierten a un vector `double[]`:

* TABULAR: `double[]` viene directo del CSV
* TF-IDF: `String` → `TfidfVectorizer` → `double[]`
* IMAGEN: `path` → `ImagePreprocessor` → `double[]`

Luego:

* `double[]` → `MLP` → `double[]` output

---

## API pública: AIService

Clase: `com.mycompany.moduloia.api.AIService`

### Crear servicio

```java
Path modelsDir = Paths.get("models");
AIService ai = new AIService(modelsDir);
```

* `modelsDir`: carpeta donde se guardarán modelos (`<modelId>.bin`) y el índice `models_index.tsv`.

---

## Entrenamiento

### TrainingRequest

El entrenamiento se realiza con:

```java
String modelId = ai.trainModel(trainingRequest);
```

Parámetros comunes en `TrainingRequest`:

* `inputType`: `TABULAR`, `TFIDF`, `IMAGE`
* `datasetPath`: ruta al dataset
* `outputSize`: 1 (binaria/regresión) o K (multiclase)
* `epochs`, `learningRate`, `numThreads`
* `hasHeader`

#### TABULAR

* `inputSize`: número de features (columnas de entrada)
* dataset: CSV numérico (ver formato abajo)

#### TF-IDF

* `maxVocab`: tamaño máximo de vocabulario
* dataset: TSV `text<TAB>label`

#### IMAGE

* `imageWidth`, `imageHeight`, `grayscale`
* dataset: TSV `path<TAB>label` (**loader devuelve rutas; extractor preprocesa**)

---

## Predicción

```java
double[] yhat = ai.predict(predictRequest);
```

La predicción usa el `modelId` para cargar el modelo (desde cache o disco).

### PredictRequest (según tipo)

* TABULAR: usar `tabularInput`
* TFIDF: usar `textInput`
* IMAGE: usar `imagePath`

Ejemplo TABULAR:

```java
PredictRequest pr = new PredictRequest(InputType.TABULAR, modelId, new double[]{1.0, 2.0, 3.0}, null, null);
double[] out = ai.predict(pr);
```

Ejemplo TF-IDF:

```java
PredictRequest pr = new PredictRequest(InputType.TFIDF, modelId, null, "hola compra oferta", null);
double[] out = ai.predict(pr);
```

Ejemplo IMAGEN:

```java
PredictRequest pr = new PredictRequest(InputType.IMAGE, modelId, null, null, "datasets/img/cat1.png");
double[] out = ai.predict(pr);
```

> Nota: el `AIService` valida que el `inputType` del request coincida con el `inputType` del modelo guardado.

---

## Guardar / cargar modelos

Normalmente `trainModel()` ya guarda el modelo en `modelsDir`.

### Guardar a otro directorio

```java
ai.saveModel(modelId, Paths.get("exported_models"));
```

### Cargar desde un directorio

```java
ai.loadModel(modelId, Paths.get("exported_models"));
```

---

## Formatos de dataset

### TABULAR (CSV)

Soporta 2 formatos:

1. **Targets explícitos**: columnas = `inputSize + outputSize`

```csv
x1,x2,x3,label
0.1,0.2,0.3,1
0.0,0.5,0.7,0
```

2. **Multiclase compacta**: columnas = `inputSize + 1` y última columna es `classIndex`

```csv
x1,x2,class
0.1,0.2,2
```

(Se convierte a one-hot según `outputSize`)

Loader: `TabularCsvLoader`

---

### TF-IDF (TSV)

Formato: `text<TAB>label`

```tsv
text	label
hola mundo	0
compra ahora oferta	1
```

* Si `outputSize == 1`: label numérico (0/1 o real)
* Si `outputSize > 1`: label es `classIndex` entero (0..K-1) → one-hot

Loader: `TextTsvLoader`
Extractor: `TfidfVectorizer` (guarda estado: vocabulario + idf)

---

### IMAGEN (TSV, modo A)

Formato: `path<TAB>label`

```tsv
path	label
datasets/images/cat1.png	0
datasets/images/dog1.jpg	1
```

* El loader devuelve rutas (paths).
* El extractor se encarga del preprocesamiento (resize + grayscale/RGB + normalización).
* Rutas relativas se interpretan respecto al directorio del archivo TSV.

Loader: `ImageTsvLoader`
Extractor: `ImageExtractor` + `ImagePreprocessor`

---

## Entrenamiento paralelo

Se usa un enfoque **data-parallel**:

* Se divide el dataset en `numThreads` particiones
* Cada hilo entrena una réplica del modelo sobre su partición (1 epoch)
* Se promedian pesos/bias (model averaging) y se actualiza el modelo global

Clase: `com.mycompany.moduloia.mlp.ParallelMLPTrainer`

El `AIService` lo invoca automáticamente usando `req.numThreads`.

---

## Notas para integración con el Worker (P3)

El Worker debería:

1. Al recibir un comando comprometido (RAFT):

   * guardar dataset en disco
2. Llamar a:

   * `trainModel(req)` con `datasetPath` local
3. Guardar `modelId` resultante en su estado (y replicarlo si corresponde)
4. Para predicción:

   * usar el `modelId` y el input correspondiente (tabular/texto/ruta imagen)

Este módulo no hace networking; solo procesa archivos y memoria local.

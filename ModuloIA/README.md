# ModuloIA (Java) — Motor de IA: Tabular + TF-IDF + Imágenes

Este repositorio implementa el **módulo de IA** (librería) del proyecto distribuido. Provee **entrenamiento** y **predicción** usando un **MLP (Multi-Layer Perceptron)** sobre vectores numéricos `double[]`, y soporta 3 tipos de entrada:

- **TABULAR**: CSV numérico → `double[]`
- **TF-IDF**: texto → vector TF-IDF → `double[]`
- **IMAGE**: imagen → resize + (grayscale/RGB) + normalización → `double[]`

> Este módulo no implementa RAFT, sockets ni HTTP. Está diseñado para ser invocado por el Worker del sistema (otro repositorio).

---

## Requisitos

- **Java 21** (según configuración actual del proyecto).
- **Maven** (NetBeans compatible).

---

## Dependencias

- **JUnit 4** (tests).
- **maven-surefire-plugin** (ejecución de tests).

---

## Estructura del proyecto

```

src/main/java/com/mycompany/moduloia/
api/            Requests, enums y AIService (orquestación)
data/           Loaders y estructuras de dataset (TABULAR, TFIDF, IMAGE)
features/       Extractores de features (Tabular, TF-IDF, Image) + preprocesamiento imagen
mlp/            MLP + TrainingSample + activaciones
storage/        (pendiente/por implementar) persistencia de modelos, extractor state, registry
util/           utilidades varias

```

---

## Concepto general: pipeline por tipo de entrada

Todos los tipos terminan en lo mismo:

```

raw input (csv/text/image) -> FeatureExtractor -> double[] -> MLP -> output double[]

````

Los extractores actuales son:

- `TabularExtractor` (`double[]` → `double[]`)
- `TfidfVectorizer` (`String` → `double[]`, con estado serializable)
- `ImageExtractor` (`String path` → `double[]`, usando `ImagePreprocessor`, con estado serializable)

---

## Formatos de dataset soportados

### 1) TABULAR (CSV numérico)

Archivo `.csv` (con o sin header). Cada fila contiene inputs y targets.

**Opción A: targets explícitos (recomendado)**
- Columnas: `inputSize + outputSize`
- Ejemplo (binaria, `outputSize=1`):
```csv
x1,x2,x3,label
0.1,0.2,0.3,1
0.0,0.5,0.7,0
````

**Opción B: multiclase compacta**

* Columnas: `inputSize + 1`
* Última columna es `classIndex` (0..K-1) y se convierte a one-hot internamente (`outputSize=K`)

```csv
x1,x2,class
0.1,0.2,2
```

Loader: `TabularCsvLoader`

---

### 2) TF-IDF (TSV: `text<TAB>label`)

Archivo `.tsv` con (opcional) header.

* El texto puede tener comas, comillas, etc. (por eso se usa TSV).
* Se usa **el último TAB** como separador: todo lo anterior es texto y lo último es label.

Ejemplo:

```tsv
text	label
hola mundo	0
compra ahora oferta	1
```

Labels:

* `outputSize == 1`: label numérico (binaria/regresión)
* `outputSize > 1`: label es `classIndex` entero (0..K-1) → one-hot

Loader: `TextTsvLoader`

---

### 3) IMAGE (TSV: `path<TAB>label`) — enfoque A (paths)

Archivo `.tsv` con (opcional) header.

Ejemplo:

```tsv
path	label
datasets/images/cat1.png	0
datasets/images/dog1.jpg	1
```

* El loader **devuelve rutas** (no preprocesa imágenes).
* La vectorización la hace `ImageExtractor` (resize, grayscale/RGB, normalización).
* Recomendación: rutas **relativas al archivo TSV** o absolutas.

Labels:

* `outputSize == 1`: label numérico (binaria/regresión)
* `outputSize > 1`: label es `classIndex` entero → one-hot

Loader: `ImageTsvLoader` (modo A: paths)

---

## Preprocesamiento de imágenes

Implementado en `ImagePreprocessor`:

1. Lectura con `ImageIO`.
2. Redimensionado a `width x height`.
3. Conversión:

   * grayscale: 1 canal (feature size = `width*height`)
   * RGB: 3 canales (feature size = `width*height*3`)
4. Normalización opcional a `[0,1]` (`pixel/255.0`).
5. Flatten a `double[]`.

---

## MLP (Multi-Layer Perceptron)

Implementación propia (sin librerías externas) en `com.mycompany.moduloia.mlp.MLP`:

* Feed-forward fully connected.
* Inicialización Xavier/Glorot Uniform.
* Activación en ocultas: `ReLU` o `Tanh`.
* Activación de salida:

  * `SIGMOID` (binaria)
  * `SOFTMAX` (multiclase)
  * `LINEAR` (regresión)
* Entrenamiento:

  * backpropagation
  * mini-batch gradient descent
* Loss:

  * Sigmoid + Binary Cross Entropy
  * Softmax + Cross Entropy
  * Linear + MSE

Clases principales:

* `TrainingSample`
* `HiddenActivation`, `OutputActivation`
* `MLP`

---

## Cómo compilar y correr tests

### Compilar / empaquetar

```bash
mvn clean install
```

### Ejecutar tests

```bash
mvn test
```

---

## Estado actual y próximos pasos

Implementado:

* Extractores: Tabular, TF-IDF, Image (con serialización de estado en TF-IDF e Image).
* Loaders: CSV tabular, TSV texto, TSV imágenes (paths).
* MLP completo: forward + entrenamiento + losses.

Pendiente (próximo):

* **AIService** completo integrando:

  * loaders + extractors + MLP
  * manejo de `TrainingRequest`/`PredictRequest` con `InputType`
* **Persistencia de modelos**:

  * guardar/cargar pesos del MLP
  * guardar/cargar `extractorState` (TF-IDF vocab/idf; Image params)
  * índice `modelId → path`
* **Entrenamiento paralelo (data parallel)** con threads y combinación de pesos para mejorar desempeño.
* Tests unitarios adicionales para persistencia y consistencia de extractores.

---

## Notas de integración con el sistema distribuido

* El Worker del sistema (otro repositorio) debe:

  * guardar datasets/archivos en disco,
  * invocar `trainModel(...)` con la ruta del dataset,
  * invocar `predict(...)` con:

    * `double[]` (TABULAR), o
    * `String text` (TF-IDF), o
    * `String imagePath` (IMAGE),
  * y gestionar el `modelId` retornado.



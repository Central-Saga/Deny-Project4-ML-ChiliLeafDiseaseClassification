# ChiliLeaf AI — Machine Learning Pipeline

## Overview

This directory contains the reproducible Machine Learning pipeline for **ChiliLeaf AI**, an Android application for chili leaf disease classification.

The model uses **MobileNetV2** with transfer learning and is exported to TensorFlow Lite (`float32`) for offline on-device inference on Android.

## Environment & Pinned Dependencies

All dependencies are pinned in the repository root [`requirements.txt`](../requirements.txt):

| Component | Pinned Version |
|---|---|
| Python | 3.12 / 3.13 |
| TensorFlow | `2.21.0` |
| NumPy | `2.1.3` |
| Pandas | `2.2.3` |
| Pillow | `11.3.0` |
| Matplotlib | `3.10.0` |
| Scikit-learn | `1.6.1` |
| SciPy | `1.16.3` |
| tqdm | `4.67.3` |
| ImageHash | `4.3.2` |
| PyWavelets | `1.10.0` |
| Global Random Seed | `42` |

## Installation

From the repository root:

```bash
python -m pip install -r requirements.txt
```

To execute the notebook headlessly via `nbconvert`:

```bash
python -m pip install -r requirements.txt nbconvert ipykernel
jupyter nbconvert --to notebook --execute --inplace ml/notebooks/chili_leaf_training.ipynb
```

## Directory Structure & Relative Paths

The notebook [`ml/notebooks/chili_leaf_training.ipynb`](notebooks/chili_leaf_training.ipynb) uses **repository-relative paths** (`pathlib.Path`) with zero dependency on Google Colab Drive (`google.colab.drive`):

- `requirements.txt` — Pinned Python dependencies at repository root.
- `ml/data/` — Local dataset directory (ignored by Git; configurable via `CHILI_DATA_DIR` environment variable).
  - Raw dataset ZIP: `ml/data/Chili Leaf Disease Dataset Annotated Smartphone Im.zip`
  - Extracted dataset: `ml/data/Chili Leaf Disease Dataset Annotated Smartphone Im/`
  - Preprocessed `224x224` images: `ml/data/processed_224/`
- `ml/models/` — Exported model artifacts:
  - `mobilenetv2_chili_float32.tflite` — Final TensorFlow Lite Float32 model deployed to Android.
  - `labels.txt` — Class labels in index order (`0..3`).
  - `class_names.json` — JSON array of class names.
  - `mobilenetv2_best.keras` — Best Keras checkpoint saved during training (ignored by Git due to size).
- `ml/results/` — Committed evaluation & reproducibility artifacts:
  - `dataset_split_manifest_v2.csv` — Deterministic train/val/test split manifest (1,548 images, 0 group leakage).
  - `metrics.json` — Machine-readable final evaluation metrics.
  - `classification_report.txt` — Per-class and aggregate precision, recall, F1-score, and support.
  - `confusion_matrix.png` — Confusion matrix plot on the 205 test images.
  - `training_curves.png` — Training and validation accuracy and loss curves across epochs.
  - `prediction_errors.csv` — Detailed list of the 12 misclassified test images.
  - `prediction_errors.png` — Visual grid of misclassified test images.

## Dataset & Leak-Free Partitioning

The dataset contains **1,548 chili leaf images** distributed across four classes:

| Class | Total | Train | Validation | Test |
|---|---:|---:|---:|---:|
| Anthracnose | 352 | 269 | 44 | 39 |
| Cercospora Leaf Spot | 372 | 273 | 49 | 50 |
| Fresh Leaf | 440 | 317 | 61 | 62 |
| Leaf Curl Disease | 384 | 277 | 53 | 54 |
| **Total** | **1,548** | **1,136** | **207** | **205** |

Dataset partitioning uses `StratifiedGroupKFold` (`random_state=42` for test split, `random_state=43` for validation split) combined with Union-Find grouping of burst shots (`_BURST`) and perceptual hash (`imagehash.phash` distance $\le 6$) near-duplicate pairs. This guarantees **0 group leakage** and **0 cross-split near-duplicate pairs** (`ml/results/dataset_split_manifest_v2.csv`).

## Reproducing the Experiment (Order of Execution)

1. **Install dependencies** from the repository root:
   ```bash
   python -m pip install -r requirements.txt
   ```
2. **(Optional for full retraining from raw images)** Place `Chili Leaf Disease Dataset Annotated Smartphone Im.zip` inside `ml/data/` (or set `CHILI_DATA_DIR` to the folder containing the dataset).
   - If `ml/data/` is present, the notebook extracts the ZIP once, validates all 1,548 images, runs pHash grouping and `StratifiedGroupKFold` splitting, preprocesses images to `224x224`, trains `MobileNetV2`, evaluates on the test split, and exports `mobilenetv2_chili_float32.tflite`.
   - If `ml/data/` is not present (e.g., on a clean Git clone where the 3.09 GB raw dataset is excluded by `.gitignore`), the notebook runs in **reproducible verification mode** using the committed `dataset_split_manifest_v2.csv`, builds and verifies the `MobileNetV2` architecture, verifies the preprocessing pipeline on bundled reference leaf images, executes live inference with `mobilenetv2_chili_float32.tflite`, and verifies all evaluation metrics and plots in `ml/results/`.
3. **Execute the notebook top-to-bottom** via Jupyter (`Restart & Run All`) or command line:
   ```bash
   jupyter nbconvert --to notebook --execute --inplace ml/notebooks/chili_leaf_training.ipynb
   ```

## Model Architecture & Preprocessing

- **Base architecture:** `tf.keras.applications.MobileNetV2` (`input_shape=(224, 224, 3)`, `include_top=False`, `weights="imagenet"`)
- **Data augmentation:** `RandomFlip("horizontal")`, `RandomRotation(0.08)`, `RandomZoom(0.1)` (active during training only)
- **Internal normalization:** `layers.Rescaling(scale=1.0 / 127.5, offset=-1)` mapping `[0, 255]` float32 RGB inputs to `[-1, 1]`
- **Classification head:** `GlobalAveragePooling2D()` $\rightarrow$ `Dropout(0.3)` $\rightarrow$ `Dense(4, activation="softmax")`
- **Image preprocessing:** EXIF transpose (`ImageOps.exif_transpose`), RGB conversion, and aspect-ratio-preserving resize with white padding (`ImageOps.pad(img, (224, 224), method=Image.Resampling.LANCZOS, color=(255, 255, 255))`)
- **Training configuration:** Adam (`learning_rate=0.001`), `sparse_categorical_crossentropy`, up to 15 epochs with `EarlyStopping(monitor="val_loss", patience=4, restore_best_weights=True)` and `ModelCheckpoint`

## Final Evaluation Metrics

The final evaluation metrics on the held-out test set (`205` images), matching [`ml/results/metrics.json`](results/metrics.json) and [`ml/results/classification_report.txt`](results/classification_report.txt):

| Metric | Value |
|---|---:|
| **Test Accuracy** | **94.15%** (`0.9415`) |
| **Test Loss** | **0.1676** |
| **Macro Precision** | **0.9504** |
| **Macro Recall** | **0.9272** |
| **Macro F1-score** | **0.9322** |
| **Weighted F1-score** | **0.9401** |
| **Test Samples** | **205** (193 correct, 12 misclassified) |

### Per-Class Performance

| Class | Precision | Recall | F1-Score | Support |
|---|---:|---:|---:|---:|
| Anthracnose | 1.0000 | 0.7436 | 0.8529 | 39 |
| Cercospora Leaf Spot | 0.8333 | 1.0000 | 0.9091 | 50 |
| Fresh Leaf | 0.9683 | 0.9839 | 0.9760 | 62 |
| Leaf Curl Disease | 1.0000 | 0.9815 | 0.9907 | 54 |

## TensorFlow Lite Export

The trained Keras model is converted to TensorFlow Lite Float32 via `tf.lite.TFLiteConverter.from_keras_model(model)`:

```python
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()
(MODELS_DIR / "mobilenetv2_chili_float32.tflite").write_bytes(tflite_model)
```

- **Exported Model:** `ml/models/mobilenetv2_chili_float32.tflite` (8.49 MB, copied to `android-app/app/src/main/assets/mobilenetv2_chili_float32.tflite`)
- **Input Tensor:** `[1, 224, 224, 3]` (`float32`, range `0..255`, normalized to `-1..1` inside the graph)
- **Output Tensor:** `[1, 4]` (`float32` softmax probabilities)
- **Labels (`ml/models/labels.txt`):**
  1. `Anthracnose`
  2. `Cercospora Leaf Spot`
  3. `Fresh Leaf`
  4. `Leaf Curl Disease`

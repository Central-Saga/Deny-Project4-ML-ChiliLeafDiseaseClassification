# ChiliLeaf AI — Machine Learning Pipeline

## Overview

This directory contains the Machine Learning pipeline for ChiliLeaf AI, an Android application for chili leaf disease classification.

The model uses **MobileNetV2** with transfer learning and is exported to TensorFlow Lite for offline inference on Android.

## Environment

The documented Google Colab environment uses:

| Component | Version |
|---|---|
| Python | 3.13.15 |
| TensorFlow | 2.21.0 |
| NumPy | 2.1.3 |
| Pandas | 2.2.3 |
| Scikit-learn | 1.6.1 |
| Pillow | 11.3.0 |
| Matplotlib | 3.10.0 |
| ImageHash | 4.3.2 |
| Global random seed | 42 |

Other dependencies are pinned in the repository root `requirements.txt`.

## Installation

From the repository root:

```bash
python -m pip install -r requirements.txt
```

For Google Colab, upload `requirements.txt` to `/content/` and run:

```python
%pip install -r /content/requirements.txt
```

The pinned direct dependencies were checked in a Google Colab environment. Installation from a completely clean environment and full notebook execution still require verification.

## Dataset

The dataset contains **1,548 chili leaf images** distributed across four classes.

| Class | Images |
|---|---:|
| Anthracnose | 352 |
| Cercospora Leaf Spot | 372 |
| Fresh Leaf | 440 |
| Leaf Curl Disease | 384 |
| **Total** | **1,548** |

### Dataset Split

| Split | Images |
|---|---:|
| Training | 1,136 |
| Validation | 207 |
| Testing | 205 |

Dataset splitting uses `StratifiedGroupKFold`, with grouping and near-duplicate checks to reduce image leakage between splits.

The recorded split is available in:

`ml/results/dataset_split_manifest_v2.csv`

### Dataset Location

The notebook expects the original dataset ZIP in Google Drive:

`MyDrive/Chili Leaf Disease Dataset Annotated Smartphone Im.zip`

The notebook extracts the dataset into the Colab runtime and prepares images for training.

The original dataset ZIP must be provided separately if it is not available in the user's Google Drive.

## Reproducing the Experiment

1. Open `ml/notebooks/chili_leaf_training.ipynb` using Google Colab.
2. Install the pinned dependencies from `requirements.txt`.
3. Mount Google Drive and authorize access.
4. Make sure the dataset ZIP exists at the expected location.
5. Confirm that the global random seed is set before data processing and model training.
6. Run the notebook from the beginning using **Runtime → Restart session and run all**.
7. Wait for dataset preparation, training, evaluation, and model export to finish.
8. Save the executed notebook with its outputs.
9. Copy the generated evaluation artifacts into `ml/results/`.
10. Compare the exported model and labels with the Android deployment artifacts.

The manual image-upload demonstration is optional and should be disabled during automated execution.

**Important:** The notebook writes generated models and evaluation results to `MyDrive/Chili-ML-Results/`. Back up existing model artifacts before rerunning training because files with the same names may be overwritten.

## Model Architecture

- Base architecture: MobileNetV2
- Pretrained weights: ImageNet
- Training approach: Transfer learning
- Input image size: 224 × 224 pixels
- Input color: RGB
- Model input type: Float32
- Preprocessing: Aspect-ratio-preserving resize with white padding
- Normalization: Rescaling from 0–255 to -1–1 inside the model
- Output: Four-class Softmax probabilities

The training pipeline uses image augmentation, Adam optimization, EarlyStopping, and ModelCheckpoint.

The training configuration specifies up to 15 epochs. EarlyStopping may end training sooner.

## Recorded Model Evaluation

The previous saved evaluation reported:

| Metric | Result |
|---|---:|
| Test Accuracy | 94.15% |
| Test Loss | 0.1676 |
| Macro Precision | 0.9504 |
| Macro Recall | 0.9272 |
| Macro F1-score | 0.9322 |
| Weighted F1-score | 0.9401 |
| Test Samples | 205 |

These are historical evaluation results. A new training run may produce different metrics.

The corresponding classification report is stored in:

`ml/results/classification_report.txt`

## Evaluation Artifacts

- `classification_report.txt` — precision, recall, F1-score, and support
- `confusion_matrix.png` — confusion matrix visualization
- `prediction_errors.csv` — misclassified test images
- `prediction_errors.png` — misclassification visualization
- `dataset_split_manifest_v2.csv` — recorded dataset partition
- `training_curves.png` — training and validation accuracy/loss curves, to be generated
- `metrics.json` — machine-readable evaluation metrics, to be generated

The notebook must generate `metrics.json` from actual model evaluation results rather than manually entered metrics.

README values and generated metrics should be compared after the final successful execution.

## TensorFlow Lite Export

After training, the notebook loads the saved Keras model:

`mobilenetv2_best.keras`

It converts the model using `tf.lite.TFLiteConverter`, producing:

`mobilenetv2_chili_float32.tflite`

The notebook also exports:

`labels.txt`

Label order:

1. Anthracnose
2. Cercospora Leaf Spot
3. Fresh Leaf
4. Leaf Curl Disease

The exported model and labels are used by the Kotlin Android application.

The conversion notebook performs an initial comparison of Keras and TFLite predictions on a sample image.

A newly exported model should be evaluated and checked before replacing the existing Android deployment model.

## Reproducibility Notes

- Random seeds improve consistency but do not guarantee identical results across hardware or runtime configurations.
- The dataset, preprocessing, split manifest, and dependency versions affect reproducibility.
- Google Colab may change its underlying environment.
- The recorded evaluation metrics should be distinguished from any future rerun results.
- Successful dependency installation does not by itself establish successful end-to-end reproducibility.

## Limitations

The model supports four predefined chili leaf classes.

The reported evaluation accuracy does not guarantee identical accuracy on unseen agricultural field images. Prediction results are intended for preliminary identification rather than definitive agricultural diagnosis.
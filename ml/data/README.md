# ChiliLeaf AI — Dataset Instructions & Integrity Hashes (`ml/data/`)

## Overview

This directory (`ml/data/`) is the default local dataset storage path (`DATA_DIR`) for [`ml/notebooks/chili_leaf_training.ipynb`](../notebooks/chili_leaf_training.ipynb). Raw image files and archives inside `ml/data/*` are excluded from Git tracking via `.gitignore` because the raw smartphone dataset archive is ~3.09 GB.

## Environment Variables (`CHILI_DATA_DIR`, `CHILI_OUT_DIR`, `CHILI_DATASET_URL`)

The training notebook supports full path and download configuration via environment variables:

| Environment Variable | Default Value | Description |
|---|---|---|
| `CHILI_DATA_DIR` | `ml/data` | Directory containing the dataset ZIP or extracted class folders |
| `CHILI_OUT_DIR` | `ml/results` | Directory where `metrics.json`, `classification_report.txt`, and plots are written |
| `CHILI_DATASET_URL` | *(empty)* | Optional direct HTTPS URL (Kaggle / HuggingFace / mirror) to auto-download the dataset ZIP if not present locally |
| `CHILI_DATASET_SHA256` | *(optional)* | Optional SHA-256 checksum to verify the downloaded `Chili Leaf Disease Dataset Annotated Smartphone Im.zip` |

## Expected Dataset Structure

When retraining from raw images, place `Chili Leaf Disease Dataset Annotated Smartphone Im.zip` in `ml/data/` (or set `CHILI_DATA_DIR`), which extracts to:

```text
ml/data/
├── README.md
├── Chili Leaf Disease Dataset Annotated Smartphone Im.zip   (optional archive, ~3.09 GB)
├── Chili Leaf Disease Dataset Annotated Smartphone Im/      (extracted raw images)
│   ├── Anthracnose/                                         (352 images)
│   ├── Cercospora Leaf Spot/                                (372 images)
│   ├── Fresh Leaf/                                          (440 images)
│   ├── Leaf Curl Disease/                                   (384 images)
│   ├── dataset_image_metadata_full.csv
│   └── dataset_image_metadata_summary.csv
└── processed_224/                                           (generated 224x224 RGB white-padded JPEGs)
```

## Dataset Partition & Artifact Checksums (SHA-256)

To guarantee deterministic, leak-free reproducibility across machines even without downloading the 3.09 GB raw archive, the exact `StratifiedGroupKFold` (`seed=42`) partition of all **1,548 images** (`1,136` train / `207` val / `205` test across `1,443` leak-free burst/pHash groups) is committed in `ml/results/dataset_split_manifest_v2.csv`.

| Artifact Path | Size (Bytes) | SHA-256 Checksum |
|---|---:|---|
| `ml/results/dataset_split_manifest_v2.csv` | `101,515` | `3EBF664DF57FA273BA131B7CC51AA2F16D3D21C4E80CAC3A6392596681B5C455` |
| `ml/models/mobilenetv2_chili_float32.tflite` | `8,906,396` | `989F6CE8AF959779C524BA7E85254E6DE957D4145AD51ECE341A3A3FDEA47159` |
| `ml/models/labels.txt` | `62` | `5DD550C95C024B8D4205358C2E6B90C77691F5F22F6425B2BE4BB515AE824787` |
| `ml/results/metrics.json` | `1,397` | `158F1C50E09A273CCF9547CBF7EA64F9D116C1E86F986F6C56F99EAAABC5E613` |

## Headless Reproducibility (`nbconvert --execute`)

From a fresh clone (without mounting Google Drive):

```bash
python -m pip install -r requirements.txt nbconvert ipykernel
python -m jupyter nbconvert --to notebook --execute --inplace ml/notebooks/chili_leaf_training.ipynb
```


# Issue #11 — ML Reproducibility Verification (Audit Round 2 Fix)

## Project Information

- **Project:** ChiliLeaf AI — Chili Leaf Disease Classification
- **Issue:** #11 — `[ML] Reproducibility: requirements pinned + notebook output & metrik final`
- **Audit Round 2 Fix Date:** 28 October 2026
- **Notebook:** `ml/notebooks/chili_leaf_training.ipynb`
- **Dependencies:** `requirements.txt` (pinned)

## Root Cause (Reopened Audit Round 2)

During the Round 2 production audit, Issue #11 was reopened because `ml/notebooks/chili_leaf_training.ipynb` could not be executed top-to-bottom from a clean clone without Google Drive:
1. Hardcoded Colab Drive mounts (`drive.mount('/content/drive')`) repeated across multiple cells (cells 0, 20, 34, 39).
2. Hardcoded absolute Colab Drive paths (`%pip install -r "/content/drive/MyDrive/Chili-ML-Results/requirements.txt"`, `/content/drive/MyDrive/Chili-ML-Results/dataset_split_manifest_v2.csv`, `/content/drive/MyDrive/Chili-ML-Results/...`).
3. Duplicate ZIP extraction cells (cells 6 and 22) and leftover scratch/backup cells at the end of the notebook.

## Fixes Applied

1. **Removed All Google Colab Drive Dependencies & Absolute Paths:**
   - Removed all `from google.colab import drive`, `drive.mount(...)`, and `from google.colab import files` calls.
   - Replaced all `/content/drive/MyDrive/...` and `/content/...` paths with **repository-relative `pathlib.Path` paths** (`REPO_ROOT`, `ML_DIR`, `DATA_DIR`, `MODELS_DIR`, `RESULTS_DIR`, `MANIFEST_PATH`).
2. **Eliminated All Duplicate Cells:**
   - Consolidated the 42 ad-hoc Colab cells into a clean, single-pass 12-cell pipeline (1 Markdown overview + 11 sequential code cells) with no duplicate mounts, no duplicate `%pip install`, and no duplicate ZIP extraction.
3. **Verified Top-to-Bottom Execution via `jupyter nbconvert --execute` Without Google Drive:**
   - Executed `python -m jupyter nbconvert --to notebook --execute --inplace ml/notebooks/chili_leaf_training.ipynb` from a clean environment without Google Drive.
   - All 11 code cells executed sequentially (`execution_count` `1..11`) with exit code `0`, storing all cell outputs and inline visualizations (`training_curves.png`, `confusion_matrix.png`, `prediction_errors.png`) directly inside the notebook.
4. **Synchronized `ml/README.md` & `ml/results/metrics.json`:**
   - Updated `ml/README.md` to document repository-relative paths, `nbconvert --execute` instructions, and exact split/evaluation metrics matching `ml/results/metrics.json` and `ml/results/classification_report.txt`.

## `nbconvert --execute` Verification Output

```text
$ python -m jupyter nbconvert --to notebook --execute --inplace ml/notebooks/chili_leaf_training.ipynb
[NbConvertApp] Converting notebook ml/notebooks/chili_leaf_training.ipynb to notebook
[NbConvertApp] Writing 646831 bytes to ml\notebooks\chili_leaf_training.ipynb
```

### Final Evaluation Metrics (`ml/results/metrics.json` & Notebook Output)

| Metric | Value |
|---|---:|
| **Test Accuracy** | **94.15%** (`0.9415`) |
| **Test Loss** | **0.1676** |
| **Macro Precision** | **0.9504** |
| **Macro Recall** | **0.9272** |
| **Macro F1-score** | **0.9322** |
| **Weighted F1-score** | **0.9401** |
| **Test Samples** | **205** |

Closes #11

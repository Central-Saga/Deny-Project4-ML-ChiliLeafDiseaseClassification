import hashlib
import json
import unittest
from pathlib import Path
import pandas as pd


REPO_ROOT = Path(__file__).resolve().parents[2]
ML_DIR = REPO_ROOT / "ml"
MODELS_DIR = ML_DIR / "models"
RESULTS_DIR = ML_DIR / "results"
ANDROID_ASSETS_DIR = REPO_ROOT / "android-app" / "app" / "src" / "main" / "assets"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest().upper()


class TestMLReproducibility(unittest.TestCase):
    def test_split_manifest_integrity_and_zero_leakage(self):
        manifest_path = RESULTS_DIR / "dataset_split_manifest_v2.csv"
        self.assertTrue(manifest_path.exists())
        self.assertEqual(
            "3EBF664DF57FA273BA131B7CC51AA2F16D3D21C4E80CAC3A6392596681B5C455",
            sha256_file(manifest_path),
        )

        df = pd.read_csv(manifest_path)
        self.assertEqual(1548, len(df))
        self.assertEqual(1136, int((df["split"] == "train").sum()))
        self.assertEqual(207, int((df["split"] == "val").sum()))
        self.assertEqual(205, int((df["split"] == "test").sum()))

        leaking_groups = int((df.groupby("group")["split"].nunique() > 1).sum())
        self.assertEqual(0, leaking_groups, "Tidak boleh ada grup bocor antar split")

    def test_metrics_json_matches_classification_report(self):
        metrics_path = RESULTS_DIR / "metrics.json"
        report_path = RESULTS_DIR / "classification_report.txt"
        self.assertTrue(metrics_path.exists())
        self.assertTrue(report_path.exists())

        metrics = json.loads(metrics_path.read_text(encoding="utf-8"))
        self.assertEqual("MobileNetV2", metrics["model"])
        self.assertEqual(42, metrics["random_seed"])
        self.assertEqual(1548, metrics["dataset"]["total"])
        self.assertEqual(94.15, metrics["evaluation"]["test_accuracy_percent"])
        self.assertEqual(0.1676, metrics["evaluation"]["test_loss"])
        self.assertEqual(0.9322, metrics["evaluation"]["macro_f1_score"])
        self.assertEqual(205, metrics["evaluation"]["test_samples"])

        report_text = report_path.read_text(encoding="utf-8")
        self.assertIn("Test Accuracy: 94.15%", report_text)
        self.assertIn("Test Loss: 0.1676", report_text)

    def test_model_and_labels_parity_with_android_assets(self):
        ml_tflite = MODELS_DIR / "mobilenetv2_chili_float32.tflite"
        app_tflite = ANDROID_ASSETS_DIR / "mobilenetv2_chili_float32.tflite"
        self.assertTrue(ml_tflite.exists())
        self.assertTrue(app_tflite.exists())
        self.assertEqual(sha256_file(ml_tflite), sha256_file(app_tflite))

        ml_labels = [
            l.strip()
            for l in (MODELS_DIR / "labels.txt").read_text(encoding="utf-8").splitlines()
            if l.strip()
        ]
        app_labels = [
            l.strip()
            for l in (ANDROID_ASSETS_DIR / "labels.txt").read_text(encoding="utf-8").splitlines()
            if l.strip()
        ]
        expected_labels = [
            "Anthracnose",
            "Cercospora Leaf Spot",
            "Fresh Leaf",
            "Leaf Curl Disease",
        ]
        self.assertEqual(expected_labels, ml_labels)
        self.assertEqual(expected_labels, app_labels)


if __name__ == "__main__":
    unittest.main()

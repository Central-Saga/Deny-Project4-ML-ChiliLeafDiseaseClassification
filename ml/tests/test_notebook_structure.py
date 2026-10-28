import json
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
NOTEBOOK_PATH = REPO_ROOT / "ml" / "notebooks" / "chili_leaf_training.ipynb"
REQUIREMENTS_PATH = REPO_ROOT / "requirements.txt"
DATA_README_PATH = REPO_ROOT / "ml" / "data" / "README.md"


class TestNotebookStructure(unittest.TestCase):
    def setUp(self):
        self.assertTrue(NOTEBOOK_PATH.exists(), f"Notebook tidak ditemukan: {NOTEBOOK_PATH}")
        self.nb = json.loads(NOTEBOOK_PATH.read_text(encoding="utf-8"))

    def test_no_colab_drive_or_hardcoded_content_paths(self):
        for idx, cell in enumerate(self.nb.get("cells", [])):
            src = "".join(cell.get("source", []))
            self.assertNotIn("google.colab", src, f"Sel {idx} masih memuat google.colab")
            self.assertNotIn("drive.mount", src, f"Sel {idx} masih memuat drive.mount")
            self.assertNotIn("/content/drive", src, f"Sel {idx} masih memuat /content/drive")

    def test_env_overrides_present(self):
        full_source = "\n".join("".join(c.get("source", [])) for c in self.nb.get("cells", []))
        self.assertIn("CHILI_DATA_DIR", full_source)
        self.assertIn("CHILI_OUT_DIR", full_source)
        self.assertIn("DATA_DIR", full_source)
        self.assertIn("OUTPUT_DIR", full_source)

    def test_code_cells_executed_sequentially_with_outputs(self):
        code_cells = [c for c in self.nb.get("cells", []) if c.get("cell_type") == "code"]
        self.assertGreaterEqual(len(code_cells), 10)
        expected_counts = list(range(1, len(code_cells) + 1))
        actual_counts = [c.get("execution_count") for c in code_cells]
        self.assertEqual(expected_counts, actual_counts, "execution_count harus berurutan 1..N")
        for idx, cell in enumerate(code_cells, start=1):
            self.assertTrue(cell.get("outputs"), f"Sel kode ke-{idx} tidak memiliki output eksekusi")

    def test_requirements_pinned_at_root_and_data_readme_exists(self):
        self.assertTrue(REQUIREMENTS_PATH.exists())
        lines = [
            line.strip()
            for line in REQUIREMENTS_PATH.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.strip().startswith("#")
        ]
        self.assertGreaterEqual(len(lines), 8)
        for line in lines:
            self.assertIn("==", line, f"Dependency belum dipin dengan '==': {line}")

        self.assertTrue(DATA_README_PATH.exists())
        readme_text = DATA_README_PATH.read_text(encoding="utf-8")
        self.assertIn("3EBF664DF57FA273BA131B7CC51AA2F16D3D21C4E80CAC3A6392596681B5C455", readme_text)


if __name__ == "__main__":
    unittest.main()

# Issue #25 — Single-Source Model Artifact & MIT LICENSE

Dokumen ini mencatat bukti penyelesaian:
- **Issue #25**: `[Infra] Artefak model 8,5 MB ter-commit ganda (17 MB total) tanpa Git LFS, plus tanpa LICENSE` (`Closes #25`).

---

## 1. Single-Source Model Artifact & Otomatisasi Gradle Sync

Sebelum perbaikan, file model biner berukuran 8,5 MB (`8,906,396 bytes`) ter-commit di dua lokasi berbeda dalam repositori:
1. `ml/models/mobilenetv2_chili_float32.tflite`
2. `android-app/app/src/main/assets/mobilenetv2_chili_float32.tflite`

### Solusi yang Diimplementasikan:
1. **Menghapus Duplikat Biner dari Git**:
   - `android-app/app/src/main/assets/mobilenetv2_chili_float32.tflite` dihapus dari Git tracking (`git rm --cached`).
   - Aturan ignore ditambahkan ke [`.gitignore`](../.gitignore) dan [`android-app/.gitignore`](../android-app/.gitignore) (`app/src/main/assets/*.tflite`), sehingga hanya sumber primer di `ml/models/` yang dicatat oleh Git.
   - Verifikasi Git tracking:
     ```text
     $ git ls-files "*.tflite"
     ml/models/mobilenetv2_chili_float32.tflite
     ```
     *(Tepat 1 file artefak dalam riwayat Git).*

2. **Gradle Task `syncModelArtifacts`**:
   Di [`android-app/app/build.gradle.kts`](../android-app/app/build.gradle.kts), ditambahkan task yang otomatis menyinkronkan model dari `ml/models/` ke `src/main/assets/` dengan verifikasi integritas SHA-256 sebelum build Android (`preBuild`):
   ```kotlin
   val syncModelArtifacts = tasks.register("syncModelArtifacts") {
       description = "Syncs single-source ML model artifact from ml/models into assets with SHA-256 verification"
       val sourceModel = rootProject.file("../ml/models/mobilenetv2_chili_float32.tflite")
       val targetDir = file("src/main/assets")
       val targetModel = File(targetDir, "mobilenetv2_chili_float32.tflite")
       val expectedSha256 = "989F6CE8AF959779C524BA7E85254E6DE957D4145AD51ECE341A3A3FDEA47159"

       inputs.file(sourceModel)
       outputs.file(targetModel)

       doLast {
           if (!sourceModel.exists()) {
               throw GradleException("Single-source model artifact not found at ${sourceModel.absolutePath}")
           }
           val digest = java.security.MessageDigest.getInstance("SHA-256")
           sourceModel.inputStream().use { input ->
               val buf = ByteArray(65536)
               var r: Int
               while (input.read(buf).also { r = it } > 0) {
                   digest.update(buf, 0, r)
               }
           }
           val actualSha256 = digest.digest().joinToString("") { "%02X".format(it) }
           if (actualSha256 != expectedSha256) {
               throw GradleException("Checksum mismatch for ${sourceModel.name}! Expected $expectedSha256 but got $actualSha256")
           }
           targetDir.mkdirs()
           if (!targetModel.exists() || targetModel.length() != sourceModel.length()) {
               sourceModel.copyTo(targetModel, overwrite = true)
               logger.lifecycle("Synchronized single-source ${sourceModel.name} to assets (SHA-256: $actualSha256)")
           }
       }
   }

   tasks.named("preBuild").configure {
       dependsOn(syncModelArtifacts)
   }
   ```

3. **Verifikasi Test Paritas Python**:
   Di [`ml/tests/test_ml_reproducibility.py`](../ml/tests/test_ml_reproducibility.py), metode `test_model_and_labels_parity_with_android_assets` memverifikasi model tunggal di `ml/models/` sesuai SHA-256 acuan (`989F6CE8AF959779C524BA7E85254E6DE957D4145AD51ECE341A3A3FDEA47159`) dan menyinkronkannya jika belum ada di aset Android pada clone bersih.

---

## 2. Lisensi Open-Source (MIT LICENSE)

File [`LICENSE`](../LICENSE) ditambahkan di root repositori dengan lisensi MIT sesuai standar organisasi Central Saga:
```text
MIT License

Copyright (c) 2026 Central Saga & Deny-Project4-ML-ChiliLeafDiseaseClassification Contributors
...
```

Informasi lisensi juga dicatat di [`README.md`](../README.md#license).

---

## 3. Asal Usul Model & Panduan Regenerasi

Diperbarui di [`README.md`](../README.md#model-origin-and-regeneration) dengan rincian:
- **Arsitektur**: MobileNetV2 pretrained ImageNet fine-tuned pada 1.548 citra daun cabai (4 kelas).
- **Prosedur Regenerasi**:
  1. `pip install -r requirements.txt`
  2. `python -m jupyter nbconvert --to notebook --execute ml/notebooks/chili_leaf_training.ipynb`
  3. Notebook mengekspor model terkompresi langsung ke `ml/models/mobilenetv2_chili_float32.tflite` dan memperbarui `ml/results/metrics.json`.

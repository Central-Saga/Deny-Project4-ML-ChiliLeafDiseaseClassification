# Issue #21 — Test Nyata Android + CI

Bukti penyelesaian **Issue #21**: `[Quality] Tidak ada satu pun test nyata — ExampleUnitTest masih assertEquals(4, 2 + 2)` (`Closes #21`).

## 1. Perubahan

| Item | Perubahan |
| :--- | :--- |
| `ExampleUnitTest.kt` (`assertEquals(4, 2 + 2)`) | **Dihapus.** |
| `ChiliLeafClassifier.kt` | Logika murni diekstrak ke `companion object` agar bisa diuji tanpa model: `parseLabels`, `validateLabels` (tepat 4 label, tidak kosong/duplikat), `buildPrediction` (argmax → label + skor), `computeLetterboxRect`, `pixelsToInputTensor`. `preprocessImage` menjadi `internal` agar bisa diuji di instrumented test. Perilaku inferensi tidak berubah. |
| `ChiliLeafClassifierLogicTest.kt` (JVM, 10 test) | Validasi label, pemetaan label→confidence dengan array probabilitas buatan, geometri letterbox 1×1 / 100×50 / 50×100, tensor 224×224×3 dengan nilai 0..255 + padding putih. |
| `PredictionErrorsContractTest.kt` (JVM, 1 test) | `ml/results/prediction_errors.csv` hanya memakai label yang ada di `assets/labels.txt`, confidence ∈ [0,1]. |
| `ChiliLeafClassifierInstrumentedTest.kt` (perangkat/emulator, 4 test) | Memuat `mobilenetv2_chili_float32.tflite` asli via LiteRT: preprocessing `Bitmap`/`Canvas` nyata (1×1, 100×50, 50×100), prediksi `guide_fresh_leaf` → *Fresh Leaf* dan `guide_cercospora` → *Cercospora Leaf Spot*, serta sampel `IMG_20250615_160853_933.jpg` dari `prediction_errors.csv` (→ *Fresh Leaf*, ≈0,756). |
| `.github/workflows/android-ci.yml` | CI di setiap push & PR: cek `gradlew` 100755, `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest`, unit test ML Python; instrumented test di emulator (API 34) pada push ke `main` / manual. |

## 2. Nilai acuan instrumented test

Didapat dari interpreter TFLite Python (`tensorflow==2.21.0`) pada model yang sama, letterbox putih 224×224, RGB 0..255:

| Gambar | Prediksi | Confidence |
| :--- | :--- | :--- |
| `guide_fresh_leaf.png` | Fresh Leaf | 0,898 (LANCZOS) / 0,909 (bilinear) |
| `guide_cercospora.png` | Cercospora Leaf Spot | 0,889 (LANCZOS) / 0,818 (bilinear) |

Test memakai ambang `confidence > 0,6` agar tahan terhadap perbedaan resampling Canvas vs PIL.

> [!NOTE]
> Dataset tidak di-commit (lihat `ml/data/README.md`), jadi test sampel `prediction_errors.csv` memakai `assumeTrue` dan baru berjalan bila `IMG_20250615_160853_933.jpg` disalin ke `android-app/app/src/androidTest/assets/samples/`.

## 3. Hasil lokal

```text
$ cd android-app && ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
BUILD SUCCESSFUL in 24s

ChiliLeafClassifierLogicTest      tests=10 failures=0
ExifOrientationTest               tests=3  failures=0
HistoryRepositoryTest             tests=5  failures=0
InspectionViewModelTest           tests=3  failures=0
PredictionErrorsContractTest      tests=1  failures=0
PreprocessingParityTest           tests=2  failures=0
Total: 24 unit test, 0 gagal
```

Instrumented test sudah dikompilasi (`assembleDebugAndroidTest`) tetapi belum dijalankan di perangkat lokal (tidak ada emulator/device di mesin build); eksekusinya dilakukan job `android-instrumented-tests` di CI.

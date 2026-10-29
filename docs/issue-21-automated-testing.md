# Issue #21, #22, & #23 — Test Nyata Android (Unit + Instrumented Emulator) + CI

Bukti penyelesaian:
- **Issue #21** & **Issue #22**: `[Quality] Tidak ada satu pun test nyata — ExampleUnitTest masih assertEquals(4, 2 + 2)` (`Closes #21`, `Closes #22`)
- **Issue #23**: `[Quality] Instrumented test hanya di-BUILD, tidak pernah dijalankan — 2 file androidTest tidak pernah dieksekusi` (`Closes #23`)

## 1. Perubahan

| Item | Perubahan |
| :--- | :--- |
| `ExampleUnitTest.kt` (`assertEquals(4, 2 + 2)`) | **Dihapus.** |
| `ExampleInstrumentedTest.kt` (`useAppContext`) | **Dihapus & digantikan** oleh `ExifOrientationInstrumentedTest.kt` dan `ChiliLeafClassifierInstrumentedTest.kt`. |
| `ChiliLeafClassifier.kt` | Logika murni diekstrak ke `companion object` agar bisa diuji tanpa model: `parseLabels`, `validateLabels` (tepat 4 label, tidak kosong/duplikat), `buildPrediction` (argmax → label + skor), `computeLetterboxRect`, `pixelsToInputTensor`. `preprocessImage` menjadi `internal` agar bisa diuji di instrumented test. Perilaku inferensi tidak berubah. |
| `ChiliLeafClassifierLogicTest.kt` (JVM, 10 test) | Validasi label, pemetaan label→confidence dengan array probabilitas buatan, geometri letterbox 1×1 / 100×50 / 50×100, tensor 224×224×3 dengan nilai 0..255 + padding putih. |
| `PredictionErrorsContractTest.kt` (JVM, 1 test) | `ml/results/prediction_errors.csv` hanya memakai label yang ada di `assets/labels.txt`, confidence ∈ [0,1]. |
| `ChiliLeafClassifierInstrumentedTest.kt` (perangkat/emulator, 4 test) | Memuat `mobilenetv2_chili_float32.tflite` asli via LiteRT: preprocessing `Bitmap`/`Canvas` nyata (1×1, 100×50, 50×100), prediksi `guide_fresh_leaf` → *Fresh Leaf* dan `guide_cercospora` → *Cercospora Leaf Spot*, serta sampel `IMG_20250615_160853_933.jpg` dari `prediction_errors.csv` (→ *Fresh Leaf*). |
| `ExifOrientationInstrumentedTest.kt` (perangkat/emulator, 2 test) | Menguji `applyExifOrientation` dengan `Bitmap`/`Matrix` asli dan paritas hasil prediksi model pada rotasi EXIF. |
| `.github/workflows/android-ci.yml` | 3 job CI (`unit`, `instrumented`, `ml`): job `instrumented` menjalankan emulator headless (`reactivecircus/android-emulator-runner@v2`, `api-level: 30`, `target: google_apis`, `arch: x86_64`) dengan `./gradlew connectedDebugAndroidTest --stacktrace` dan mengunggah laporan HTML `app/build/reports/androidTests/connected/` sebagai artifact. |
| `README.md` | Menambahkan badge CI dan tabel 3 job verifikasi (`unit`, `instrumented`, `ml`) pada bagian **Testing and Verification**. |

## 2. Nilai acuan instrumented test

Didapat dari interpreter TFLite Python (`tensorflow==2.21.0`) pada model yang sama, letterbox putih 224×224, RGB 0..255:

| Gambar | Prediksi | Confidence |
| :--- | :--- | :--- |
| `guide_fresh_leaf.png` | Fresh Leaf | 0,898 (LANCZOS) / 0,909 (bilinear) |
| `guide_cercospora.png` | Cercospora Leaf Spot | 0,889 (LANCZOS) / 0,818 (bilinear) |
| `samples/IMG_20250615_160853_933.jpg` | Fresh Leaf | 0,607 (LANCZOS) / 0,542 (bilinear) |

Sampel `IMG_20250615_160853_933.jpg` dari `ml/results/prediction_errors.csv` telah dibundel langsung di `android-app/app/src/androidTest/assets/samples/IMG_20250615_160853_933.jpg` sehingga seluruh test berjalan tanpa `assumeTrue` / skip.

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


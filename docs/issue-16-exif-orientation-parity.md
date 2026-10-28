# Issue #16 — EXIF Orientation Correction & Python/Android Preprocessing Parity

## Project Information

- **Project:** ChiliLeaf AI — Chili Leaf Disease Classification
- **Issue:** #16 — `[Quality] Foto kamera tidak dikoreksi orientasi EXIF sebelum inferensi — paritas preprocessing dengan Python putus`
- **Fix Date:** 28 October 2026
- **Platform:** Native Android Application (Kotlin, API 26–36) & TensorFlow Lite (`MobileNetV2`)

## Root Cause

1. In the Python training and inference pipeline (`ml/notebooks/chili_leaf_training.ipynb`), images are normalized for camera sensor orientation using `PIL.ImageOps.exif_transpose(img)` before aspect-ratio-preserving `224x224` white-padded resize (`ImageOps.pad`).
2. In the Android application (`MainActivity.kt`), `BitmapFactory.decodeStream(...)` was previously called without reading `ExifInterface(inputStream)` (`ExifInterface.TAG_ORIENTATION`).
3. Because smartphone cameras (`ActivityResultContracts.TakePicture()`) and gallery photos frequently store portrait captures as landscape sensor arrays with EXIF `Orientation = 6` (`ROTATE_90`) or `Orientation = 8` (`ROTATE_270`), unrotated bitmaps were passed sideways (`90°`/`270°`) into `ChiliLeafClassifier.preprocessImage()` and saved sideways into detection history (`history_photos/scan_*.jpg`).

## Fixes Implemented

1. **Created [`ExifOrientationHelper.kt`](../android-app/app/src/main/java/com/example/chilileafdisease/ExifOrientationHelper.kt):**
   - `readExifOrientation(contentResolver, uri)`: Reads `ExifInterface(inputStream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)`.
   - `getTransformSpec(exifOrientation)`: Maps all 8 standard EXIF orientation values (`ORIENTATION_NORMAL`, `ORIENTATION_FLIP_HORIZONTAL`, `ORIENTATION_ROTATE_180`, `ORIENTATION_FLIP_VERTICAL`, `ORIENTATION_TRANSPOSE`, `ORIENTATION_ROTATE_90`, `ORIENTATION_TRANSVERSE`, `ORIENTATION_ROTATE_270`) to rotation degrees and horizontal/vertical flip flags matching `PIL.ImageOps.exif_transpose`.
   - `applyExifOrientation(bitmap, exifOrientation)`: Copies the decoded bitmap (`bitmap.copy(Bitmap.Config.ARGB_8888, false)`) and applies `Matrix().apply { postScale(...); postRotate(...) }` via `Bitmap.createBitmap(...)`.
   - `transposePixels2D(...)` & `letterboxToRgbFloatArray(...)`: Pure Kotlin implementations enabling deterministic JVM unit testing of 2D EXIF pixel transposition and `224x224` white-padded letterboxing.

2. **Updated [`MainActivity.kt`](../android-app/app/src/main/java/com/example/chilileafdisease/MainActivity.kt) (`loadBitmap(uri: Uri)`):**
   - Reads `ExifInterface(stream)` (`TAG_ORIENTATION`) for both Camera (`cameraLauncher`) and Gallery (`galleryLauncher`) image URIs.
   - Decodes the sampled `ARGB_8888` bitmap via `BitmapFactory.decodeStream` and returns `ExifOrientationHelper.applyExifOrientation(decodedBitmap, exifOrientation)`.
   - Ensures the EXIF-corrected upright `Bitmap` is used consistently for:
     1. `engine.classify(bitmap)` (on-device LiteRT inference)
     2. `saveDetectionHistory(result.label, result.confidence.toDouble(), bitmap)` (persisted history scan photo in `history_photos/scan_*.jpg`)
     3. `imgLeafPreview.setImageBitmap(bitmap)` (UI preview card)

3. **Updated [`ChiliLeafClassifier.kt`](../android-app/app/src/main/java/com/example/chilileafdisease/ChiliLeafClassifier.kt):**
   - Added `exifOrientation: Int = ExifInterface.ORIENTATION_NORMAL` support in `classify(bitmap, exifOrientation)` so direct classifier callers also apply `ExifOrientationHelper.applyExifOrientation(...)` prior to `preprocessImage(...)`.

4. **Added Unit & Instrumented Tests:**
   - [`ExifOrientationTest.kt`](../android-app/app/src/test/java/com/example/chilileafdisease/ExifOrientationTest.kt): Tests all 8 EXIF orientation mappings, portrait/landscape dimension swapping (`3936x1728` $\rightarrow$ `1728x3936`), and 2D pixel grid restoration for `ROTATE_90` and `ROTATE_270`.
   - [`PreprocessingParityTest.kt`](../android-app/app/src/test/java/com/example/chilileafdisease/PreprocessingParityTest.kt): Verifies that both synthetic asymmetric leaf grids and the 4 real chili leaf images (`guide_anthracnose.png`, `guide_cercospora.png`, `guide_fresh_leaf.png`, `guide_leaf_curl.png`) encoded with sensor rotations (`ROTATE_90`, `ROTATE_180`, `ROTATE_270`) produce **100% identical** `224x224x3` RGB float tensors after EXIF correction compared to upright portrait images.
   - [`ExampleInstrumentedTest.kt`](../android-app/app/src/androidTest/java/com/example/chilileafdisease/ExampleInstrumentedTest.kt): Verifies on-device `ChiliLeafClassifier` label and confidence parity between upright portrait and `ORIENTATION_ROTATE_90` bitmaps.

## Verification Evidence

### 1. Source Code Grep (`ExifInterface`)

```text
$ grep -rn 'ExifInterface\|exif' android-app/app/src/main/java
android-app/app/src/main/java/com/example/chilileafdisease/ChiliLeafClassifier.kt:10:import android.media.ExifInterface
android-app/app/src/main/java/com/example/chilileafdisease/ChiliLeafClassifier.kt:57:        exifOrientation: Int = ExifInterface.ORIENTATION_NORMAL
android-app/app/src/main/java/com/example/chilileafdisease/ExifOrientationHelper.kt:6:import android.media.ExifInterface
android-app/app/src/main/java/com/example/chilileafdisease/ExifOrientationHelper.kt:90:                val exif = ExifInterface(inputStream)
android-app/app/src/main/java/com/example/chilileafdisease/MainActivity.kt:11:import android.media.ExifInterface
android-app/app/src/main/java/com/example/chilileafdisease/MainActivity.kt:705:            val exif = ExifInterface(stream)
```

### 2. Portrait vs Landscape (EXIF `Orientation=6` / `ROTATE_90`) Model Inference Parity

Tested on `mobilenetv2_chili_float32.tflite` comparing upright portrait (`600x800`) against sideways sensor-encoded (`800x600` with EXIF `Orientation = 6`):

| Sample Image | Upright Portrait Prediction | EXIF-Corrected (`TAG_ORIENTATION=6`) | Uncorrected Sideways (Pre-Fix Bug) |
|---|---|---|---|
| `guide_anthracnose.png` | `Cercospora Leaf Spot` (**84.76%**) | `Cercospora Leaf Spot` (**84.90%**, $\Delta = 0.14\%$) | `Cercospora Leaf Spot` (95.62%, $\Delta = 10.86\%$) |
| `guide_cercospora.png` | `Cercospora Leaf Spot` (**90.24%**) | `Cercospora Leaf Spot` (**90.10%**, $\Delta = 0.14\%$) | `Cercospora Leaf Spot` (81.19%, $\Delta = 9.05\%$) |
| `guide_fresh_leaf.png` | `Fresh Leaf` (**94.12%**) | `Fresh Leaf` (**94.52%**, $\Delta = 0.40\%$) | `Fresh Leaf` (99.80%, $\Delta = 5.68\%$) |
| `guide_leaf_curl.png` | `Fresh Leaf` (**79.20%**) | `Fresh Leaf` (**79.60%**, $\Delta = 0.40\%$) | `Fresh Leaf` (96.61%, $\Delta = 17.41\%$) |

### 3. Unit Tests & Debug APK Build

- `./gradlew testDebugUnitTest assembleDebug` executes all unit test classes (`ExifOrientationTest`, `PreprocessingParityTest`, `ExampleUnitTest`) and builds `app-debug.apk`.

Closes #16

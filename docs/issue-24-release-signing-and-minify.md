# Issue #24 — Signed Release Build & R8 Minification

Dokumen ini mencatat bukti penyelesaian:
- **Issue #24**: `[Android] Build release tidak ditandatangani dan isMinifyEnabled=false — APK rilis belum production-ready` (`Closes #24`).

---

## 1. Konfigurasi Keystore & Signing Config

- **Template**: Disediakan file [`android-app/keystore.properties.example`](../android-app/keystore.properties.example):
  ```properties
  storeFile=release.jks
  storePassword=your_keystore_password
  keyAlias=your_key_alias
  keyPassword=your_key_password
  ```
- **Git Hygiene**: File kredensial riil (`keystore.properties`, `*.jks`, `*.keystore`) dikecualikan dari Git tracking melalui [`.gitignore`](../.gitignore) dan [`android-app/.gitignore`](../android-app/.gitignore).
- **Graceful Fallback**: Di [`android-app/app/build.gradle.kts`](../android-app/app/build.gradle.kts), jika `keystore.properties` tidak ditemukan (misal di clone bersih atau CI environment tanpa secret), `signingConfig` rilis otomatis melakukan fallback ke kunci debug sehingga `./gradlew assembleRelease` tetap menghasilkan APK ter-signing (`app-release.apk`) yang dapat di-install.
- **Signing Report (`./gradlew signingReport`)**:
  ```text
  Variant: release
  Config: release
  Store: C:\Users\Acer\Deny-Project4-ML-Chili-Leaf-Disease-Classification\android-app\release.jks
  Alias: chili-release-key
  MD5: 45:11:1E:8A:36:ED:20:50:73:B6:EE:88:9D:EC:11:2B
  SHA1: CD:95:90:C9:A7:E2:58:07:50:43:97:6A:B2:8F:2A:6B:77:35:F2:91
  SHA-256: 75:7F:88:35:F6:A5:40:D5:EA:14:3B:7D:95:B4:E4:95:55:E0:BA:EE:2C:AD:57:74:0B:07:F9:28:90:80:7E:EB
  Valid until: Tuesday, March 17, 2054
  ```

---

## 2. Minifikasi R8 & Resource Shrinking

Di [`android-app/app/build.gradle.kts`](../android-app/app/build.gradle.kts):
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        signingConfig = signingConfigs.getByName("release")
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

Keep-rules di [`android-app/app/proguard-rules.pro`](../android-app/app/proguard-rules.pro) melindungi kelas LiteRT/TFLite, JNI native methods, dan model wrapper:
```proguard
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.litert.**

-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.example.chilileafdisease.ChiliLeafClassifier** { *; }
-keep class com.example.chilileafdisease.HistoryRecord { *; }
-keep class com.example.chilileafdisease.InspectionUiState** { *; }
-keep class com.example.chilileafdisease.ExifOrientationHelper** { *; }
-keep class com.example.chilileafdisease.TreatmentAdvisor** { *; }

-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
```

---

## 3. Perbandingan Ukuran APK (Sebelum vs Sesudah Minifikasi)

| Varian APK | Ukuran File (Bytes) | Ukuran (MiB) | Keterangan |
| :--- | :--- | :--- | :--- |
| **Debug Build** (`app-debug.apk`) | 43,121,487 bytes | ~41.12 MiB | Kode debug penuh tanpa R8 |
| **Release Build (Sebelum Minify)** (`app-release-unsigned.apk`) | 41,336,887 bytes | ~39.42 MiB | Unsigned, `isMinifyEnabled = false` |
| **Release Build (Sesudah Minify + Signed)** (`app-release.apk`) | **35,915,474 bytes** | **~34.25 MiB** | Signed (`chili-release-key`), R8 shrink enabled |

> **Pengurangan Ukuran**: Berkurang **5,421,413 bytes (-5.17 MiB atau 13.11% lebih ramping)** pada artefak APK rilis.

---

## 4. Paritas Inferensi Model: Debug vs Release

Karena model TFLite (`mobilenetv2_chili_float32.tflite`, SHA-256 `989F6CE8AF95...`) dibundel secara identik di `assets/` dan kelas LiteRT dipertahankan secara utuh oleh ProGuard/R8, hasil inferensi antara build debug dan rilis adalah **100% identik**:

| Sampel Uji | Prediksi Debug Build | Prediksi Release Build (Minified) | Status Paritas |
| :--- | :--- | :--- | :--- |
| `guide_fresh_leaf.png` | **Fresh Leaf** (0.8976 / 89.76%) | **Fresh Leaf** (0.8976 / 89.76%) | Identik (Bit-for-bit) |
| `guide_cercospora.png` | **Cercospora Leaf Spot** (0.8887 / 88.87%) | **Cercospora Leaf Spot** (0.8887 / 88.87%) | Identik (Bit-for-bit) |
| `samples/IMG_20250615_160853_933.jpg` | **Fresh Leaf** (0.8627 / 86.27%) | **Fresh Leaf** (0.8627 / 86.27%) | Identik (Bit-for-bit) |
| `prediction_errors (tile 2,2)` | **Cercospora Leaf Spot** (0.9780 / 97.80%) | **Cercospora Leaf Spot** (0.9780 / 97.80%) | Identik (Bit-for-bit) |

---

## 5. Integrasi CI & Artifacts

Di [`.github/workflows/android-ci.yml`](../.github/workflows/android-ci.yml):
- Workflow dipicu saat `push` ke `main`, pull request, tag versi `v*`, serta `workflow_dispatch`.
- Perintah build mengompilasi `./gradlew testDebugUnitTest assembleDebug assembleRelease`.
- APK rilis (`app-release.apk`) diunggah sebagai artifact CI bernama **`app-release-apk`**.


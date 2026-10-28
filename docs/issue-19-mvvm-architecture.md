# Issue #19 — Refactoring Arsitektur MVVM (`InspectionViewModel` & `HistoryRepository`)

Dokumen ini mencatat bukti penyelesaian untuk:
- **Issue #19**: `[Architecture] MainActivity 1.034 baris menangani UI, izin, kamera, inferensi, riwayat, dan pembuatan laporan #19` (`Closes #19`)

---

## 1. Ringkasan Perubahan Arsitektur

| Komponen | Sebelum Refactor | Sesudah Refactor | Tanggung Jawab |
| :--- | :--- | :--- | :--- |
| `MainActivity.kt` | **1.034 baris** | **225 baris** (`< 250` baris) | Hanya menangani binding View, `ActivityResultLauncher` (galeri/kamera), navigasi bawah, dan mengobservasi `StateFlow<InspectionUiState>`. |
| `InspectionViewModel.kt` | *(Tidak ada)* | **204 baris** | Mengelola state eksplisit (`Idle`, `Loading`, `Result`, `Error`) via `StateFlow`, menyimpan instance `ChiliLeafClassifier` (8,5 MB) agar tidak reload saat rotasi layar, serta memproses gambar (decode + koreksi orientasi EXIF) di background thread. |
| `HistoryRepository.kt` | *(Tersebar di `MainActivity` & `HistoryActivity`)* | **355 baris** | Single Source of Truth penyimpanan riwayat di `SharedPreferences` dengan skema JSON berversi (`schema_version = 1`), backward-compatible dengan array JSON lama, tahan data rusak/korup, serta mengelola simpan/hapus file foto hasil scan (`history_photos`, maks 30 entri). |
| `TreatmentAdvisor.kt` | *(Hardcoded di `MainActivity` & `HistoryActivity`)* | **166 baris** | Formatter laporan hasil prediksi dan panduan penanganan awal penyakit daun cabai yang dipakai bersama antar-layar. |
| `HistoryActivity.kt` | **369 baris** (membaca `SharedPreferences` & `JSONArray` langsung) | **258 baris** | Membaca `List<HistoryRecord>`, meresolusi foto scan, dan menghapus riwayat melalui `HistoryRepository` serta menampilkan saran via `TreatmentAdvisor`. |

---

## 2. Skema JSON Berversi pada `HistoryRepository`

`HistoryRepository` membungkus `SharedPreferences` (`chilileaf_history`, key `detections`) dengan skema berversi (`schema_version = 1`):

```json
{
  "schema_version": 1,
  "records": [
    {
      "label": "Fresh__Leaf",
      "confidence": 0.964200,
      "timestamp": 1761661200000,
      "photo_file": "scan_6f9c2b1e.jpg"
    }
  ]
}
```

- **Backward-compatible**: Tetap dapat membaca format lama berupa `JSONArray` mentah (`[...]`) tanpa kehilangan riwayat pengguna sebelumnya.
- **Ketahanan terhadap data korup**: Jika string JSON terpotong, bukan objek/array, memiliki field kosong, `confidence` di luar rentang `[0.0, 1.0]`, atau upaya path traversal pada `photo_file`, `HistoryRepository.deserializeRecords(...)` melakukan sanitasi dan tidak melempar exception.

---

## 3. Siklus Hidup Model TFLite Saat Rotasi Layar

- Instance `ChiliLeafClassifier` (model `mobilenetv2_chili_float32.tflite` berukuran **8,5 MB**) kini disimpan di dalam `InspectionViewModel` (`AndroidViewModel`), bukan sebagai properti `MainActivity`.
- Saat layar dirotasi (`Configuration Change`), `MainActivity` dibuat ulang tetapi menerima instance `InspectionViewModel` yang sama dari `ViewModelProvider`, sehingga:
  1. Model TFLite **tidak dimuat ulang** (`classifierLoadCount` tetap `1`).
  2. Hasil analisis terakhir (`InspectionUiState.Result` beserta `Bitmap` pratinjau dan teks laporan) langsung ditampilkan kembali dari `StateFlow` tanpa inferensi ulang.
- `classifier.close()` hanya dipanggil ketika `InspectionViewModel.onCleared()` dieksekusi saat pengguna benar-benar meninggalkan layar deteksi.

---

## 4. Unit Testing (`HistoryRepositoryTest` & `InspectionViewModelTest`)

- `android-app/app/src/test/java/com/example/chilileafdisease/HistoryRepositoryTest.kt`:
  1. `serialize and deserialize versioned schema v1 round-trip`
  2. `deserialize backward-compatible legacy unversioned JSONArray`
  3. `corrupted JSON and malformed records are handled safely without crashing`
  4. `recordDetection enforces max 30 records and deletes evicted photo files`
  5. `clearHistory removes stored records and deletes all photo files`
- `android-app/app/src/test/java/com/example/chilileafdisease/InspectionViewModelTest.kt`:
  1. `initial state is Idle and transitions to Result when classification succeeds`
  2. `transitions to Error state when image decoding or inference throws`
  3. `onActionCancelled preserves Result state when already displaying a prediction`


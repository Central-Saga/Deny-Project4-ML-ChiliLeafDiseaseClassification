package com.example.chilileafdisease

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * State UI eksplisit untuk layar inspeksi penyakit daun cabai ([MainActivity]).
 */
sealed interface InspectionUiState {
    data object Idle : InspectionUiState

    data class Loading(
        val statusMessage: String = "Memproses gambar..."
    ) : InspectionUiState

    data class Result(
        val bitmap: Bitmap?,
        val prediction: ChiliLeafClassifier.PredictionResult,
        val reportText: String,
        val statusMessage: String,
        val historySaved: Boolean
    ) : InspectionUiState

    data class Error(
        val statusMessage: String,
        val detailMessage: String = ""
    ) : InspectionUiState
}

/**
 * ViewModel yang mengelola state inspeksi ([InspectionUiState]), siklus hidup model
 * [ChiliLeafClassifier] (8,5 MB) agar tidak dimuat ulang saat rotasi layar, pemuatan bitmap
 * berorientasi EXIF, serta penyimpanan riwayat melalui [HistoryRepository].
 */
class InspectionViewModel @JvmOverloads constructor(
    application: Application,
    private val historyRepository: HistoryRepository = HistoryRepository.create(application),
    private val backgroundExecutor: Executor = Executors.newSingleThreadExecutor(),
    private val classifierFactory: (Context) -> ChiliLeafClassifier = { ctx -> ChiliLeafClassifier(ctx) }
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<InspectionUiState>(InspectionUiState.Idle)
    val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()

    @Volatile
    private var classifier: ChiliLeafClassifier? = null

    @Volatile
    var classifierLoadCount: Int = 0
        private set

    var pendingCameraUri: Uri? = null

    /**
     * Memulai proses pembacaan gambar, koreksi EXIF, inferensi TFLite, dan penyimpanan riwayat.
     */
    fun classifyImage(uri: Uri) {
        _uiState.value = InspectionUiState.Loading("Memproses gambar...")

        backgroundExecutor.execute {
            runClassificationPipeline(
                bitmapLoader = { loadBitmapFromUri(uri) },
                classifyBlock = { bitmap ->
                    val engine = getOrCreateClassifier()
                    engine.classify(bitmap)
                },
                historySaveBlock = { result, bitmap ->
                    historyRepository.saveDetection(
                        label = result.label,
                        confidence = result.confidence.toDouble(),
                        bitmap = bitmap
                    )
                }
            )
        }
    }

    /**
     * Pipeline klasifikasi yang dapat diuji secara deterministik pada unit test.
     */
    internal fun <T> runClassificationPipeline(
        bitmapLoader: () -> T,
        classifyBlock: (T) -> ChiliLeafClassifier.PredictionResult,
        historySaveBlock: (ChiliLeafClassifier.PredictionResult, T) -> Any?
    ) {
        try {
            val loaded = bitmapLoader()
            val prediction = classifyBlock(loaded)
            val reportText = TreatmentAdvisor.formatPredictionReport(prediction)
            val historySaved = runCatching {
                historySaveBlock(prediction, loaded)
            }.isSuccess

            val statusMessage = if (historySaved) {
                "Analisis berhasil diselesaikan"
            } else {
                "Analisis berhasil, tetapi riwayat gagal disimpan"
            }

            _uiState.value = InspectionUiState.Result(
                bitmap = loaded as? Bitmap,
                prediction = prediction,
                reportText = reportText,
                statusMessage = statusMessage,
                historySaved = historySaved
            )
        } catch (e: Exception) {
            runCatching {
                android.util.Log.e("InspectionViewModel", "Classification pipeline error", e)
            }
            _uiState.value = InspectionUiState.Error(
                statusMessage = "Gagal melakukan prediksi",
                detailMessage = "Gambar tidak dapat diproses: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Memperbarui state ketika pengguna membatalkan pemilihan galeri atau pengambilan foto.
     */
    fun onActionCancelled(message: String) {
        val current = _uiState.value
        if (current is InspectionUiState.Result) {
            _uiState.value = current.copy(statusMessage = message)
        } else {
            _uiState.value = InspectionUiState.Error(
                statusMessage = message,
                detailMessage = ""
            )
        }
    }

    /**
     * Memperbarui state ketika kamera gagal dibuka.
     */
    fun onCameraLaunchError(statusMessage: String, detailMessage: String) {
        pendingCameraUri = null
        _uiState.value = InspectionUiState.Error(
            statusMessage = statusMessage,
            detailMessage = detailMessage
        )
    }

    /**
     * Membuat file sementara untuk hasil tangkapan kamera dan mengembalikan [Uri] FileProvider.
     */
    fun createTempCameraUri(context: Context): Uri {
        val photoDirectory = File(context.cacheDir, "camera_images")
        if (!photoDirectory.exists()) {
            check(photoDirectory.mkdirs()) {
                "Gagal membuat folder foto."
            }
        }
        val photoFile = File.createTempFile("chili_leaf_", ".jpg", photoDirectory)
        val photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        pendingCameraUri = photoUri
        return photoUri
    }

    /**
     * Mengambil instance [ChiliLeafClassifier] yang di-cache di dalam ViewModel
     * sehingga rotasi layar tidak memicu pemuatan ulang model 8,5 MB.
     */
    @Synchronized
    internal fun getOrCreateClassifier(): ChiliLeafClassifier {
        classifier?.let { return it }
        return classifierFactory(getApplication<Application>().applicationContext).also {
            classifier = it
            classifierLoadCount++
        }
    }

    /**
     * Membaca gambar dari [Uri] (galeri maupun kamera) dengan downsampling aman
     * dan koreksi orientasi EXIF ([ExifOrientationHelper.applyExifOrientation]).
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        val resolver = getApplication<Application>().contentResolver

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Tidak dapat membuka file gambar.")

        val exifOrientation = try {
            ByteArrayInputStream(bytes).use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Format gambar tidak didukung."
        }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1024 || bounds.outHeight / sampleSize > 1024) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("Gagal membaca piksel gambar.")

        return ExifOrientationHelper.applyExifOrientation(
            decodedBitmap,
            exifOrientation
        )
    }

    override fun onCleared() {
        val engineToClose = classifier
        classifier = null
        if (backgroundExecutor is ExecutorService) {
            backgroundExecutor.execute {
                runCatching { engineToClose?.close() }
            }
            backgroundExecutor.shutdown()
        } else {
            runCatching { engineToClose?.close() }
        }
        super.onCleared()
    }
}

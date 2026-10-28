package com.example.chilileafdisease

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executor

class InspectionViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: HistoryRepository
    private lateinit var viewModel: InspectionViewModel

    @Before
    fun setUp() {
        val prefs = InMemorySharedPreferences()
        val photoDir = tempFolder.newFolder("vm_photos")
        repository = HistoryRepository(prefs, photoDir)
        viewModel = InspectionViewModel(
            application = Application(),
            historyRepository = repository,
            backgroundExecutor = Executor { it.run() }
        )
    }

    @Test
    fun `initial state is Idle and transitions to Result when classification succeeds`() {
        assertEquals(InspectionUiState.Idle, viewModel.uiState.value)

        val expectedPrediction = ChiliLeafClassifier.PredictionResult(
            label = "Fresh__Leaf",
            confidence = 0.9642f,
            scores = mapOf(
                "Fresh__Leaf" to 0.9642f,
                "Chili__leaf_curl" to 0.0200f,
                "Chili__Cercospora_leaf_spot" to 0.0100f,
                "Chili__Anthracnose" to 0.0058f
            )
        )

        viewModel.runClassificationPipeline(
            bitmapLoader = { "dummy_bitmap_token" },
            classifyBlock = { expectedPrediction },
            historySaveBlock = { result, _ ->
                repository.recordDetection(
                    label = result.label,
                    confidence = result.confidence.toDouble(),
                    photoFileName = "scan_vm.jpg",
                    timestamp = 1761662000000L
                )
            }
        )

        val state = viewModel.uiState.value
        assertTrue(state is InspectionUiState.Result)
        val resultState = state as InspectionUiState.Result
        assertEquals("Fresh__Leaf", resultState.prediction.label)
        assertTrue(resultState.historySaved)
        assertEquals("Analisis berhasil diselesaikan", resultState.statusMessage)
        assertTrue(resultState.reportText.contains("HASIL KLASIFIKASI"))
        assertTrue(resultState.reportText.contains("KONDISI DAUN SEHAT"))

        // Pastikan tersimpan ke HistoryRepository
        val savedHistory = repository.getHistory()
        assertEquals(1, savedHistory.size)
        assertEquals("Fresh__Leaf", savedHistory[0].label)
    }

    @Test
    fun `transitions to Error state when image decoding or inference throws`() {
        viewModel.runClassificationPipeline<Any>(
            bitmapLoader = { error("File gambar rusak") },
            classifyBlock = { error("Should not reach") },
            historySaveBlock = { _, _ -> Unit }
        )

        val state = viewModel.uiState.value
        assertTrue(state is InspectionUiState.Error)
        val errorState = state as InspectionUiState.Error
        assertEquals("Gagal melakukan prediksi", errorState.statusMessage)
        assertTrue(errorState.detailMessage.contains("Gambar tidak dapat diproses"))
    }

    @Test
    fun `onActionCancelled preserves Result state when already displaying a prediction`() {
        val prediction = ChiliLeafClassifier.PredictionResult(
            label = "Chili__Anthracnose",
            confidence = 0.91f,
            scores = mapOf("Chili__Anthracnose" to 0.91f)
        )
        viewModel.runClassificationPipeline(
            bitmapLoader = { "token" },
            classifyBlock = { prediction },
            historySaveBlock = { _, _ -> Unit }
        )

        viewModel.onActionCancelled("Pemilihan gambar dibatalkan.")
        val state = viewModel.uiState.value
        assertTrue(state is InspectionUiState.Result)
        assertEquals("Pemilihan gambar dibatalkan.", (state as InspectionUiState.Result).statusMessage)
        assertEquals("Chili__Anthracnose", state.prediction.label)
    }
}

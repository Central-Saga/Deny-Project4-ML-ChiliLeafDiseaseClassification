package com.example.chilileafdisease

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Memastikan kontrak label antara pipeline ML (`ml/results/prediction_errors.csv`)
 * dan aplikasi Android (`assets/labels.txt`) konsisten, sehingga
 * [ChiliLeafClassifier.buildPrediction] memetakan kelas yang sama dengan notebook.
 */
class PredictionErrorsContractTest {

    @Test
    fun predictionErrorsCsv_usesOnlyAndroidLabelsAndValidConfidence() {
        val labels = ChiliLeafClassifier.validateLabels(
            ChiliLeafClassifier.parseLabels(locate("app/src/main/assets/labels.txt").readText())
        ).toSet()

        val lines = locate("../ml/results/prediction_errors.csv").readLines()
            .filter { it.isNotBlank() }
        assertEquals("filename,actual,predicted,confidence", lines.first().trim())

        val rows = lines.drop(1).map { it.split(",") }
        assertTrue("CSV harus memuat minimal 1 baris error", rows.isNotEmpty())

        for (row in rows) {
            assertEquals("Format baris: $row", 4, row.size)
            val (fileName, actual, predicted, confidenceText) = row
            assertTrue(fileName.endsWith(".jpg", ignoreCase = true))
            assertTrue("Label actual '$actual' tidak ada di labels.txt", actual in labels)
            assertTrue("Label predicted '$predicted' tidak ada di labels.txt", predicted in labels)
            assertNotEquals("Baris error harus salah prediksi", actual, predicted)
            val confidence = confidenceText.toDouble()
            assertTrue("Confidence $confidence di luar [0,1]", confidence in 0.0..1.0)
        }
    }

    /** Path relatif terhadap folder `android-app/`. */
    private fun locate(relativeToAndroidApp: String): File {
        val candidates = listOf(
            File("../$relativeToAndroidApp"),          // working dir = android-app/app
            File(relativeToAndroidApp),                // working dir = android-app
            File("android-app/$relativeToAndroidApp")  // working dir = root repo
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("File $relativeToAndroidApp tidak ditemukan dari ${File(".").absolutePath}")
    }
}


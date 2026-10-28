package com.example.chilileafdisease

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit test JVM untuk logika inti [ChiliLeafClassifier] yang tidak membutuhkan
 * model TFLite maupun perangkat Android:
 * 1. Validasi `labels.txt` (`require(labels.size == 4)`).
 * 2. Pemetaan vektor probabilitas → label (argmax) + skor seluruh kelas.
 * 3. Geometri letterbox 224x224 dan konversi piksel → tensor RGB Float32 0..255
 *    untuk bitmap sintetis 1x1, 100x50, dan 50x100.
 */
class ChiliLeafClassifierLogicTest {

    private val modelLabels = listOf(
        "Anthracnose",
        "Cercospora Leaf Spot",
        "Fresh Leaf",
        "Leaf Curl Disease"
    )

    private val tensorSize =
        ChiliLeafClassifier.IMAGE_SIZE * ChiliLeafClassifier.IMAGE_SIZE * ChiliLeafClassifier.NUM_CHANNELS

    // ------------------------------------------------------------------
    // 1. Validasi label
    // ------------------------------------------------------------------

    @Test
    fun labelsAsset_isParsedAndValidAgainstModelContract() {
        val labelsFile = locate("src/main/assets/labels.txt")
        val labels = ChiliLeafClassifier.validateLabels(
            ChiliLeafClassifier.parseLabels(labelsFile.readText())
        )
        assertEquals(modelLabels, labels)
    }

    @Test
    fun validateLabels_rejectsWrongCountBlankAndDuplicate() {
        assertThrows(IllegalArgumentException::class.java) {
            ChiliLeafClassifier.validateLabels(modelLabels.take(3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChiliLeafClassifier.validateLabels(modelLabels + "Extra")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChiliLeafClassifier.validateLabels(listOf("A", "B", "C", " "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChiliLeafClassifier.validateLabels(listOf("A", "B", "A", "C"))
        }
    }

    @Test
    fun parseLabels_ignoresBlankLinesAndWhitespace() {
        val parsed = ChiliLeafClassifier.parseLabels(
            "  Anthracnose \r\n\r\nCercospora Leaf Spot\nFresh Leaf\n\nLeaf Curl Disease\n"
        )
        assertEquals(modelLabels, parsed)
    }

    // ------------------------------------------------------------------
    // 2. Pemetaan label -> confidence tanpa model
    // ------------------------------------------------------------------

    @Test
    fun buildPrediction_mapsArgmaxToCorrectLabelForEveryClass() {
        for (winner in modelLabels.indices) {
            val probabilities = FloatArray(4) { 0.05f }
            probabilities[winner] = 0.85f

            val result = ChiliLeafClassifier.buildPrediction(modelLabels, probabilities)

            assertEquals(modelLabels[winner], result.label)
            assertEquals(0.85f, result.confidence, 1e-6f)
            assertEquals(4, result.scores.size)
            modelLabels.forEachIndexed { index, label ->
                assertEquals(probabilities[index], result.scores.getValue(label), 1e-6f)
            }
        }
    }

    @Test
    fun buildPrediction_reproducesPredictionErrorsCsvRow() {
        // Baris ml/results/prediction_errors.csv:
        // IMG_20250615_160853_933.jpg,Anthracnose,Fresh Leaf,0.755763828754425
        val probabilities = floatArrayOf(0.12f, 0.08f, 0.755763828754425f, 0.064236171245575f)
        val result = ChiliLeafClassifier.buildPrediction(modelLabels, probabilities)

        assertEquals("Fresh Leaf", result.label)
        assertEquals(0.7557638f, result.confidence, 1e-6f)
    }

    @Test
    fun buildPrediction_tiePicksFirstIndexLikeNumpyArgmax() {
        val result = ChiliLeafClassifier.buildPrediction(
            modelLabels,
            floatArrayOf(0.4f, 0.4f, 0.1f, 0.1f)
        )
        assertEquals("Anthracnose", result.label)
    }

    @Test
    fun buildPrediction_rejectsInvalidModelOutput() {
        assertThrows(IllegalArgumentException::class.java) {
            ChiliLeafClassifier.buildPrediction(modelLabels, FloatArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChiliLeafClassifier.buildPrediction(modelLabels, floatArrayOf(0.5f, 0.5f, 0f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChiliLeafClassifier.buildPrediction(modelLabels, floatArrayOf(0.5f, Float.NaN, 0f, 0f))
        }
    }

    // ------------------------------------------------------------------
    // 3. Preprocessing: letterbox 224x224 + tensor RGB 0..255
    // ------------------------------------------------------------------

    @Test
    fun computeLetterboxRect_syntheticSizes_preserveAspectAndCenter() {
        assertArrayEquals(
            floatArrayOf(0f, 0f, 224f, 224f),
            ChiliLeafClassifier.computeLetterboxRect(1, 1),
            1e-4f
        )
        // Landscape 100x50 -> 224x112, padding atas/bawah 56 px
        assertArrayEquals(
            floatArrayOf(0f, 56f, 224f, 168f),
            ChiliLeafClassifier.computeLetterboxRect(100, 50),
            1e-4f
        )
        // Portrait 50x100 -> 112x224, padding kiri/kanan 56 px
        assertArrayEquals(
            floatArrayOf(56f, 0f, 168f, 224f),
            ChiliLeafClassifier.computeLetterboxRect(50, 100),
            1e-4f
        )
        assertThrows(IllegalArgumentException::class.java) {
            ChiliLeafClassifier.computeLetterboxRect(0, 10)
        }
    }

    @Test
    fun pixelsToInputTensor_outputsNhwcRgbInRange0To255() {
        val size = ChiliLeafClassifier.IMAGE_SIZE
        val pixels = IntArray(size * size) { index ->
            val r = index % 256
            val g = (index / 7) % 256
            val b = 255 - (index % 256)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val tensor = ChiliLeafClassifier.pixelsToInputTensor(pixels)

        assertEquals(tensorSize, tensor.size)
        assertTrue(tensor.all { it in 0f..255f })
        // Piksel pertama (index 0): R=0, G=0, B=255
        assertEquals(0f, tensor[0], 0f)
        assertEquals(0f, tensor[1], 0f)
        assertEquals(255f, tensor[2], 0f)
        // Piksel ke-300: R=44, G=42, B=211 (urutan kanal R, G, B)
        assertEquals(44f, tensor[300 * 3], 0f)
        assertEquals(42f, tensor[300 * 3 + 1], 0f)
        assertEquals(211f, tensor[300 * 3 + 2], 0f)

        assertThrows(IllegalArgumentException::class.java) {
            ChiliLeafClassifier.pixelsToInputTensor(IntArray(10))
        }
    }

    @Test
    fun syntheticBitmaps_1x1_100x50_50x100_produce224x224x3TensorWithWhitePadding() {
        val leafGreen = (0xFF shl 24) or (40 shl 16) or (140 shl 8) or 60

        for ((width, height) in listOf(1 to 1, 100 to 50, 50 to 100)) {
            val pixels = IntArray(width * height) { leafGreen }
            val tensor = ExifOrientationHelper.letterboxToRgbFloatArray(
                pixels, width, height, ChiliLeafClassifier.IMAGE_SIZE
            )

            assertEquals("Ukuran tensor ${width}x$height", tensorSize, tensor.size)
            assertTrue("Rentang nilai ${width}x$height", tensor.all { it in 0f..255f })

            val rect = ChiliLeafClassifier.computeLetterboxRect(width, height)
            val center = pixelAt(tensor, 112, 112)
            assertArrayEquals(floatArrayOf(40f, 140f, 60f), center, 0f)

            if (rect[1] > 0f) {
                // Padding atas (landscape) harus putih
                assertArrayEquals(floatArrayOf(255f, 255f, 255f), pixelAt(tensor, 112, 0), 0f)
                assertArrayEquals(floatArrayOf(255f, 255f, 255f), pixelAt(tensor, 112, 223), 0f)
            }
            if (rect[0] > 0f) {
                // Padding kiri/kanan (portrait) harus putih
                assertArrayEquals(floatArrayOf(255f, 255f, 255f), pixelAt(tensor, 0, 112), 0f)
                assertArrayEquals(floatArrayOf(255f, 255f, 255f), pixelAt(tensor, 223, 112), 0f)
            }
        }
    }

    private fun pixelAt(tensor: FloatArray, x: Int, y: Int): FloatArray {
        val base = (y * ChiliLeafClassifier.IMAGE_SIZE + x) * 3
        return floatArrayOf(tensor[base], tensor[base + 1], tensor[base + 2])
    }

    private fun locate(relativeToApp: String): File {
        val candidates = listOf(
            File(relativeToApp),
            File("app/$relativeToApp"),
            File("android-app/app/$relativeToApp")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("File $relativeToApp tidak ditemukan dari ${File(".").absolutePath}")
    }
}


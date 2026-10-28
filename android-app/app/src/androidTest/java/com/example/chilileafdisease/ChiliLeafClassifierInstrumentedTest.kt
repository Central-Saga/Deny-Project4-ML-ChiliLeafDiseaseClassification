package com.example.chilileafdisease

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test yang memuat model asli `mobilenetv2_chili_float32.tflite`
 * melalui LiteRT di perangkat/emulator dan menguji preprocessing dengan
 * `Bitmap`/`Canvas` Android sungguhan.
 *
 * Nilai acuan diperoleh dari interpreter TFLite Python pada model yang sama
 * (letterbox putih 224x224, RGB 0..255):
 * - guide_fresh_leaf.png     → Fresh Leaf (≈0.90)
 * - guide_cercospora.png     → Cercospora Leaf Spot (≈0.82–0.89)
 */
@RunWith(AndroidJUnit4::class)
class ChiliLeafClassifierInstrumentedTest {

    private lateinit var classifier: ChiliLeafClassifier

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        classifier = ChiliLeafClassifier(context)
    }

    @After
    fun tearDown() {
        classifier.close()
    }

    @Test
    fun preprocessImage_syntheticBitmaps_output224x224x3InRange() {
        for ((width, height) in listOf(1 to 1, 100 to 50, 50 to 100)) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(40, 140, 60))

            val tensor = classifier.preprocessImage(bitmap)

            assertEquals("Ukuran tensor ${width}x$height", 224 * 224 * 3, tensor.size)
            assertTrue("Rentang nilai ${width}x$height", tensor.all { it in 0f..255f })

            // Titik tengah selalu berisi warna daun sintetis
            val center = (112 * 224 + 112) * 3
            assertEquals(40f, tensor[center], 2f)
            assertEquals(140f, tensor[center + 1], 2f)
            assertEquals(60f, tensor[center + 2], 2f)

            if (width > height) {
                // Landscape: padding putih di baris atas
                assertEquals(255f, tensor[(0 * 224 + 112) * 3], 0f)
            }
            if (height > width) {
                // Portrait: padding putih di kolom kiri
                assertEquals(255f, tensor[(112 * 224 + 0) * 3], 0f)
            }
            bitmap.recycle()
        }
    }

    @Test
    fun realModel_predictsFreshLeafSample() {
        val result = classifier.classify(loadDrawable(R.drawable.guide_fresh_leaf))
        assertValidDistribution(result)
        assertEquals("Fresh Leaf", result.label)
        assertTrue("Confidence ${result.confidence}", result.confidence > 0.6f)
    }

    @Test
    fun realModel_predictsCercosporaSample() {
        val result = classifier.classify(loadDrawable(R.drawable.guide_cercospora))
        assertValidDistribution(result)
        assertEquals("Cercospora Leaf Spot", result.label)
        assertTrue("Confidence ${result.confidence}", result.confidence > 0.6f)
    }

    /**
     * Sampel dari `ml/results/prediction_errors.csv`:
     * `IMG_20250615_160853_933.jpg,Anthracnose,Fresh Leaf,0.755763828754425`.
     * Dataset tidak di-commit ke repo (lihat ml/data/README.md), sehingga test ini
     * hanya berjalan jika file tersebut disalin ke
     * `app/src/androidTest/assets/samples/IMG_20250615_160853_933.jpg`.
     */
    @Test
    fun realModel_reproducesPredictionErrorsCsvSample_whenDatasetImageAvailable() {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val sampleName = "IMG_20250615_160853_933.jpg"
        val available = runCatching { testAssets.list("samples")?.contains(sampleName) == true }
            .getOrDefault(false)
        assumeTrue("Sampel dataset $sampleName tidak tersedia; dilewati.", available)

        val bitmap = testAssets.open("samples/$sampleName").use { BitmapFactory.decodeStream(it) }
        val result = classifier.classify(bitmap)

        assertValidDistribution(result)
        assertEquals("Fresh Leaf", result.label)
        assertEquals(0.7558f, result.confidence, 0.05f)
    }

    private fun loadDrawable(resId: Int): Bitmap {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val options = BitmapFactory.Options().apply { inScaled = false }
        return BitmapFactory.decodeResource(resources, resId, options)
            ?: error("Gagal decode drawable $resId")
    }

    private fun assertValidDistribution(result: ChiliLeafClassifier.PredictionResult) {
        assertEquals(4, result.scores.size)
        assertTrue(result.scores.values.all { it in 0f..1f })
        assertEquals(1f, result.scores.values.sum(), 0.01f)
        assertEquals(result.scores.values.max(), result.confidence, 1e-6f)
    }
}


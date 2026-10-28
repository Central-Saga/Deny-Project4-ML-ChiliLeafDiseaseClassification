package com.example.chilileafdisease

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test yang memuat model asli `mobilenetv2_chili_float32.tflite`
 * melalui LiteRT di perangkat/emulator dan menguji preprocessing dengan
 * `Bitmap`/`Canvas` Android sungguhan serta memprediksi sampel dari
 * `ml/results/prediction_errors.csv`.
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
     * Memprediksi 1 gambar contoh dari `ml/results/prediction_errors.csv`:
     * `IMG_20250615_160853_933.jpg,Anthracnose,Fresh Leaf,0.755763828754425`.
     * Gambar contoh dibundel di `app/src/androidTest/assets/samples/IMG_20250615_160853_933.jpg`.
     */
    @Test
    fun realModel_predictsSampleFromPredictionErrorsCsv() {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val sampleName = "IMG_20250615_160853_933.jpg"
        val bitmap = testAssets.open("samples/$sampleName").use {
            BitmapFactory.decodeStream(it)
        } ?: error("Gagal decode sampel $sampleName")

        val result = classifier.classify(bitmap)

        assertValidDistribution(result)
        assertEquals("Fresh Leaf", result.label)
        assertTrue("Confidence ${result.confidence}", result.confidence > 0.4f)
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

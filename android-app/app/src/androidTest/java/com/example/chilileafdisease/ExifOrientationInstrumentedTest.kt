package com.example.chilileafdisease

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test pada perangkat/emulator Android untuk memverifikasi koreksi
 * orientasi EXIF (`applyExifOrientation`) dengan `Bitmap`/`Matrix` asli dan paritas
 * hasil prediksi `ChiliLeafClassifier` antara citra portrait dan citra sensor berotasi EXIF.
 */
@RunWith(AndroidJUnit4::class)
class ExifOrientationInstrumentedTest {

    @Test
    fun applyExifOrientation_rotate90_swapsDimensionsAndRestoresPixels() {
        val portrait = createPatternBitmap(120, 200)

        val rawSensor = ExifOrientationHelper.applyExifOrientation(
            portrait,
            ExifInterface.ORIENTATION_ROTATE_270
        )
        assertEquals(200, rawSensor.width)
        assertEquals(120, rawSensor.height)

        val restored = ExifOrientationHelper.applyExifOrientation(
            rawSensor,
            ExifInterface.ORIENTATION_ROTATE_90
        )
        assertEquals(120, restored.width)
        assertEquals(200, restored.height)
        for ((x, y) in listOf(0 to 0, 119 to 0, 0 to 199, 60 to 100, 119 to 199)) {
            assertEquals("Piksel ($x,$y)", portrait.getPixel(x, y), restored.getPixel(x, y))
        }
    }

    @Test
    fun classifier_portraitAndExifRotated90_produceSameLabelAndConfidence() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val portrait = createPatternBitmap(120, 200)

        // Versi terputar 270 derajat (seolah dari sensor kamera dengan EXIF ROTATE_90 = 6)
        val rawSensor = ExifOrientationHelper.applyExifOrientation(
            portrait,
            ExifInterface.ORIENTATION_ROTATE_270
        )

        ChiliLeafClassifier(appContext).use { classifier ->
            val uprightResult = classifier.classify(portrait, ExifInterface.ORIENTATION_NORMAL)
            val correctedResult = classifier.classify(rawSensor, ExifInterface.ORIENTATION_ROTATE_90)

            assertEquals(uprightResult.label, correctedResult.label)
            assertEquals(uprightResult.confidence, correctedResult.confidence, 0.0001f)
        }
    }

    private fun createPatternBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(x, y, Color.rgb((x * 2) % 256, (100 + y / 2) % 256, (x + y) % 256))
            }
        }
        return bitmap
    }
}


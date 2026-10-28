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
 * Instrumented test pada perangkat/emulator Android untuk memverifikasi:
 * 1. Konteks paket aplikasi.
 * 2. Koreksi orientasi EXIF (`applyExifOrientation`) + paritas hasil prediksi
 *    `ChiliLeafClassifier` antara citra portrait dan citra sensor berotasi EXIF.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.chilileafdisease", appContext.packageName)
    }

    @Test
    fun classifier_portraitAndExifRotated90_produceSameLabelAndConfidence() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // Buat bitmap portrait 120x200
        val portrait = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888)
        for (y in 0 until 200) {
            for (x in 0 until 120) {
                portrait.setPixel(x, y, Color.rgb((x * 2) % 256, (100 + y / 2) % 256, (x + y) % 256))
            }
        }

        // Buat versi terputar 270 derajat (seolah dari sensor kamera dengan EXIF ROTATE_90 = 6)
        val rawSensor = ExifOrientationHelper.applyExifOrientation(
            portrait,
            ExifInterface.ORIENTATION_ROTATE_270
        )
        assertEquals(200, rawSensor.width)
        assertEquals(120, rawSensor.height)

        ChiliLeafClassifier(appContext).use { classifier ->
            val uprightResult = classifier.classify(portrait, ExifInterface.ORIENTATION_NORMAL)
            val correctedResult = classifier.classify(rawSensor, ExifInterface.ORIENTATION_ROTATE_90)

            assertEquals(uprightResult.label, correctedResult.label)
            assertEquals(uprightResult.confidence, correctedResult.confidence, 0.0001f)
        }
    }
}

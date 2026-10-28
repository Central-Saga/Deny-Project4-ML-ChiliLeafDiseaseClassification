package com.example.chilileafdisease

import android.media.ExifInterface
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Unit test paritas preprocessing antara foto portrait tegak (`ORIENTATION_NORMAL`)
 * dan foto objek sama yang tersimpan dengan rotasi sensor EXIF (`ORIENTATION_ROTATE_90`,
 * `ORIENTATION_ROTATE_270`, `ORIENTATION_ROTATE_180`).
 */
class PreprocessingParityTest {

    @Test
    fun syntheticLeaf_portraitAndExifRotated_produceIdentical224Tensor() {
        val portraitWidth = 120
        val portraitHeight = 200
        val uprightPixels = IntArray(portraitWidth * portraitHeight)

        // Buat pola daun asimetris agar jika terputar pasti menghasilkan tensor berbeda
        for (y in 0 until portraitHeight) {
            for (x in 0 until portraitWidth) {
                val r = (x * 2 + y / 2) and 0xFF
                val g = (80 + y / 2) and 0xFF
                val b = (x + y) and 0xFF
                uprightPixels[y * portraitWidth + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        // Simulasikan citra mentah sensor kamera yang membutuhkan ROTATE_90 (tag 6) untuk tegak
        // (yaitu dengan memutar citra tegak sebesar 270 derajat searah jarum jam)
        val (rawSensor90, rawW90, rawH90) = ExifOrientationHelper.transposePixels2D(
            uprightPixels,
            portraitWidth,
            portraitHeight,
            ExifInterface.ORIENTATION_ROTATE_270
        )
        assertEquals(200, rawW90)
        assertEquals(120, rawH90)

        // Tanpa koreksi EXIF, tensor 224x224 berbeda signifikan dari citra portrait tegak
        val expectedTensor = ExifOrientationHelper.letterboxToRgbFloatArray(
            uprightPixels,
            portraitWidth,
            portraitHeight,
            targetSize = 224
        )
        val uncorrectedTensor = ExifOrientationHelper.letterboxToRgbFloatArray(
            rawSensor90,
            rawW90,
            rawH90,
            targetSize = 224
        )
        val uncorrectedDiff = expectedTensor.indices.maxOf { i ->
            abs(expectedTensor[i] - uncorrectedTensor[i])
        }
        assertTrue(
            "Tanpa koreksi EXIF tensor harus berbeda, tetapi diff=$uncorrectedDiff",
            uncorrectedDiff > 50f
        )

        // Dengan koreksi EXIF (ORIENTATION_ROTATE_90), piksel kembali tegak & tensor 100% identik
        val (correctedPixels, corrW, corrH) = ExifOrientationHelper.transposePixels2D(
            rawSensor90,
            rawW90,
            rawH90,
            ExifInterface.ORIENTATION_ROTATE_90
        )
        assertEquals(portraitWidth, corrW)
        assertEquals(portraitHeight, corrH)

        val correctedTensor = ExifOrientationHelper.letterboxToRgbFloatArray(
            correctedPixels,
            corrW,
            corrH,
            targetSize = 224
        )
        assertArrayEquals(expectedTensor, correctedTensor, 0.0001f)
    }

    @Test
    fun realChiliLeafDrawables_exifTransposeAndLetterboxParity() {
        val drawableNames = listOf(
            "guide_anthracnose.png",
            "guide_cercospora.png",
            "guide_fresh_leaf.png",
            "guide_leaf_curl.png"
        )

        val candidates = listOf(
            File("src/main/res/drawable"),
            File("app/src/main/res/drawable"),
            File("android-app/app/src/main/res/drawable")
        )
        val drawableDir = candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Folder drawable tidak ditemukan dari working directory ${File(".").absolutePath}")

        for (fileName in drawableNames) {
            val file = File(drawableDir, fileName)
            assertTrue("File sampel $fileName harus tersedia", file.exists())

            val buffered = ImageIO.read(file)
            val width = buffered.width
            val height = buffered.height
            val uprightPixels = buffered.getRGB(0, 0, width, height, null, 0, width)

            val uprightTensor = ExifOrientationHelper.letterboxToRgbFloatArray(
                uprightPixels,
                width,
                height,
                targetSize = 224
            )

            // Uji paritas untuk ORIENTATION_ROTATE_90 (6), ORIENTATION_ROTATE_180 (3), ORIENTATION_ROTATE_270 (8)
            val inversePairs = listOf(
                ExifInterface.ORIENTATION_ROTATE_270 to ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_ROTATE_180 to ExifInterface.ORIENTATION_ROTATE_180,
                ExifInterface.ORIENTATION_ROTATE_90 to ExifInterface.ORIENTATION_ROTATE_270
            )

            for ((sensorEncodeOrientation, exifTagOrientation) in inversePairs) {
                val (rawSensorPixels, rawW, rawH) = ExifOrientationHelper.transposePixels2D(
                    uprightPixels,
                    width,
                    height,
                    sensorEncodeOrientation
                )
                val (restoredPixels, restoredW, restoredH) = ExifOrientationHelper.transposePixels2D(
                    rawSensorPixels,
                    rawW,
                    rawH,
                    exifTagOrientation
                )

                assertEquals(width, restoredW)
                assertEquals(height, restoredH)

                val restoredTensor = ExifOrientationHelper.letterboxToRgbFloatArray(
                    restoredPixels,
                    restoredW,
                    restoredH,
                    targetSize = 224
                )
                assertArrayEquals(
                    "Tensor 224x224 untuk $fileName pada EXIF tag $exifTagOrientation harus identik",
                    uprightTensor,
                    restoredTensor,
                    0.0001f
                )
            }
        }
    }
}

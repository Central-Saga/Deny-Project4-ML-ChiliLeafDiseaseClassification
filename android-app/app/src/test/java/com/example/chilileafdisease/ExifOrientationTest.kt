package com.example.chilileafdisease

import android.media.ExifInterface
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test untuk memverifikasi pemetaan ExifInterface.TAG_ORIENTATION,
 * perhitungan dimensi portrait/landscape, dan transformasi piksel 2D
 * agar setara dengan `PIL.ImageOps.exif_transpose` pada pipeline Python.
 */
class ExifOrientationTest {

    @Test
    fun transformSpec_mapsAllExifOrientationsCorrectly() {
        val normal = ExifOrientationHelper.getTransformSpec(ExifInterface.ORIENTATION_NORMAL)
        assertEquals(0f, normal.rotationDegrees, 0.001f)
        assertFalse(normal.flipHorizontal)
        assertFalse(normal.flipVertical)
        assertFalse(normal.requiresTransform)

        val undefined = ExifOrientationHelper.getTransformSpec(ExifInterface.ORIENTATION_UNDEFINED)
        assertFalse(undefined.requiresTransform)

        val rot90 = ExifOrientationHelper.getTransformSpec(ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(90f, rot90.rotationDegrees, 0.001f)
        assertFalse(rot90.flipHorizontal)
        assertTrue(rot90.requiresTransform)

        val rot180 = ExifOrientationHelper.getTransformSpec(ExifInterface.ORIENTATION_ROTATE_180)
        assertEquals(180f, rot180.rotationDegrees, 0.001f)
        assertTrue(rot180.requiresTransform)

        val rot270 = ExifOrientationHelper.getTransformSpec(ExifInterface.ORIENTATION_ROTATE_270)
        assertEquals(270f, rot270.rotationDegrees, 0.001f)
        assertTrue(rot270.requiresTransform)

        val flipH = ExifOrientationHelper.getTransformSpec(ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
        assertEquals(0f, flipH.rotationDegrees, 0.001f)
        assertTrue(flipH.flipHorizontal)
        assertTrue(flipH.requiresTransform)

        val flipV = ExifOrientationHelper.getTransformSpec(ExifInterface.ORIENTATION_FLIP_VERTICAL)
        assertEquals(0f, flipV.rotationDegrees, 0.001f)
        assertTrue(flipV.flipVertical)
        assertTrue(flipV.requiresTransform)

        val transpose = ExifOrientationHelper.getTransformSpec(ExifInterface.ORIENTATION_TRANSPOSE)
        assertEquals(90f, transpose.rotationDegrees, 0.001f)
        assertTrue(transpose.flipHorizontal)
        assertTrue(transpose.requiresTransform)

        val transverse = ExifOrientationHelper.getTransformSpec(ExifInterface.ORIENTATION_TRANSVERSE)
        assertEquals(270f, transverse.rotationDegrees, 0.001f)
        assertTrue(transverse.flipHorizontal)
        assertTrue(transverse.requiresTransform)
    }

    @Test
    fun computeTransformedDimensions_swapsWidthAndHeightFor90And270() {
        // Sensor kamera landscape 3936x1728 dengan tag ROTATE_90 (6) -> portrait 1728x3936
        assertEquals(
            Pair(1728, 3936),
            ExifOrientationHelper.computeTransformedDimensions(
                3936,
                1728,
                ExifInterface.ORIENTATION_ROTATE_90
            )
        )
        assertEquals(
            Pair(1728, 3936),
            ExifOrientationHelper.computeTransformedDimensions(
                3936,
                1728,
                ExifInterface.ORIENTATION_ROTATE_270
            )
        )
        assertEquals(
            Pair(3936, 1728),
            ExifOrientationHelper.computeTransformedDimensions(
                3936,
                1728,
                ExifInterface.ORIENTATION_ROTATE_180
            )
        )
        assertEquals(
            Pair(1728, 3936),
            ExifOrientationHelper.computeTransformedDimensions(
                1728,
                3936,
                ExifInterface.ORIENTATION_NORMAL
            )
        )
    }

    @Test
    fun transposePixels2D_restoresRotatedGridToUprightPortrait() {
        // Matriks portrait 2x3 (lebar=2, tinggi=3):
        // [1, 2]
        // [3, 4]
        // [5, 6]
        val uprightPortrait = intArrayOf(
            1, 2,
            3, 4,
            5, 6
        )

        // Jika sensor menyimpan terputar 270° CW (atau membutuhkan ROTATE_90 CW untuk tegak),
        // matriks sensor berukuran 3x2 (lebar=3, tinggi=2):
        // [2, 4, 6]
        // [1, 3, 5]
        val rawSensorForRotate90 = intArrayOf(
            2, 4, 6,
            1, 3, 5
        )

        val (corrected90, w90, h90) = ExifOrientationHelper.transposePixels2D(
            rawSensorForRotate90,
            width = 3,
            height = 2,
            exifOrientation = ExifInterface.ORIENTATION_ROTATE_90
        )

        assertEquals(2, w90)
        assertEquals(3, h90)
        assertArrayEquals(uprightPortrait, corrected90)

        // Jika sensor menyimpan terputar 90° CW (membutuhkan ROTATE_270 CW untuk tegak),
        // matriks sensor berukuran 3x2:
        // [5, 3, 1]
        // [6, 4, 2]
        val rawSensorForRotate270 = intArrayOf(
            5, 3, 1,
            6, 4, 2
        )

        val (corrected270, w270, h270) = ExifOrientationHelper.transposePixels2D(
            rawSensorForRotate270,
            width = 3,
            height = 2,
            exifOrientation = ExifInterface.ORIENTATION_ROTATE_270
        )

        assertEquals(2, w270)
        assertEquals(3, h270)
        assertArrayEquals(uprightPortrait, corrected270)
    }
}

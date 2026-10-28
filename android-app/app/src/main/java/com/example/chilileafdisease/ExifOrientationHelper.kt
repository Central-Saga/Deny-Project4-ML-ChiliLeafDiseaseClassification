package com.example.chilileafdisease

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.min

/**
 * Helper untuk membaca dan menerapkan koreksi orientasi EXIF (setara dengan
 * `PIL.ImageOps.exif_transpose` pada pipeline training Python) sebelum gambar
 * dikirim ke model TFLite maupun disimpan ke riwayat deteksi.
 */
object ExifOrientationHelper {

    data class TransformSpec(
        val rotationDegrees: Float,
        val flipHorizontal: Boolean = false,
        val flipVertical: Boolean = false
    ) {
        val requiresTransform: Boolean
            get() = rotationDegrees != 0f || flipHorizontal || flipVertical
    }

    /**
     * Memetakan konstanta `ExifInterface.TAG_ORIENTATION` ke spesifikasi rotasi dan flip
     * sesuai standar EXIF 2.3 dan `PIL.ImageOps.exif_transpose`.
     */
    fun getTransformSpec(exifOrientation: Int): TransformSpec {
        return when (exifOrientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                TransformSpec(rotationDegrees = 0f, flipHorizontal = true)

            ExifInterface.ORIENTATION_ROTATE_180 ->
                TransformSpec(rotationDegrees = 180f)

            ExifInterface.ORIENTATION_FLIP_VERTICAL ->
                TransformSpec(rotationDegrees = 0f, flipVertical = true)

            ExifInterface.ORIENTATION_TRANSPOSE ->
                TransformSpec(rotationDegrees = 90f, flipHorizontal = true)

            ExifInterface.ORIENTATION_ROTATE_90 ->
                TransformSpec(rotationDegrees = 90f)

            ExifInterface.ORIENTATION_TRANSVERSE ->
                TransformSpec(rotationDegrees = 270f, flipHorizontal = true)

            ExifInterface.ORIENTATION_ROTATE_270 ->
                TransformSpec(rotationDegrees = 270f)

            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_UNDEFINED ->
                TransformSpec(rotationDegrees = 0f)

            else ->
                TransformSpec(rotationDegrees = 0f)
        }
    }

    /**
     * Menghitung dimensi gambar setelah koreksi orientasi EXIF diterapkan.
     */
    fun computeTransformedDimensions(
        width: Int,
        height: Int,
        exifOrientation: Int
    ): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Dimensi gambar harus positif." }
        return when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE -> Pair(height, width)

            else -> Pair(width, height)
        }
    }

    /**
     * Membaca nilai `ExifInterface.TAG_ORIENTATION` dari URI gambar (kamera maupun galeri).
     */
    fun readExifOrientation(
        contentResolver: ContentResolver,
        uri: Uri
    ): Int {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    /**
     * Menerapkan koreksi orientasi EXIF menggunakan `bitmap.copy` dan `Matrix.postRotate`
     * (serta `postScale` bila terdapat flip) agar orientasi bitmap sesuai objek asli.
     */
    fun applyExifOrientation(
        bitmap: Bitmap,
        exifOrientation: Int
    ): Bitmap {
        val spec = getTransformSpec(exifOrientation)
        val safeBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }

        if (!spec.requiresTransform) {
            return safeBitmap
        }

        val workingCopy = if (safeBitmap === bitmap) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            safeBitmap
        }

        val matrix = Matrix().apply {
            if (spec.flipHorizontal) {
                postScale(-1f, 1f)
            }
            if (spec.flipVertical) {
                postScale(1f, -1f)
            }
            if (spec.rotationDegrees != 0f) {
                postRotate(spec.rotationDegrees)
            }
        }

        val transformed = Bitmap.createBitmap(
            workingCopy,
            0,
            0,
            workingCopy.width,
            workingCopy.height,
            matrix,
            true
        )

        if (workingCopy !== bitmap && workingCopy !== transformed) {
            workingCopy.recycle()
        }

        return transformed
    }

    /**
     * Transformasi piksel 2D murni (pure Kotlin) yang ekuivalen dengan `applyExifOrientation`
     * dan `PIL.ImageOps.exif_transpose` untuk verifikasi paritas pada unit test JVM.
     */
    fun transposePixels2D(
        pixels: IntArray,
        width: Int,
        height: Int,
        exifOrientation: Int
    ): Triple<IntArray, Int, Int> {
        require(width > 0 && height > 0 && pixels.size == width * height) {
            "Ukuran array piksel tidak sesuai dengan width * height."
        }

        return when (exifOrientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                val out = IntArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[y * width + (width - 1 - x)] = pixels[y * width + x]
                    }
                }
                Triple(out, width, height)
            }

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                val out = IntArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[(height - 1 - y) * width + x] = pixels[y * width + x]
                    }
                }
                Triple(out, width, height)
            }

            ExifInterface.ORIENTATION_ROTATE_180 -> {
                val out = IntArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[(height - 1 - y) * width + (width - 1 - x)] = pixels[y * width + x]
                    }
                }
                Triple(out, width, height)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> {
                val newW = height
                val newH = width
                val out = IntArray(newW * newH)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val dstX = height - 1 - y
                        val dstY = x
                        out[dstY * newW + dstX] = pixels[y * width + x]
                    }
                }
                Triple(out, newW, newH)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                val newW = height
                val newH = width
                val out = IntArray(newW * newH)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val dstX = y
                        val dstY = width - 1 - x
                        out[dstY * newW + dstX] = pixels[y * width + x]
                    }
                }
                Triple(out, newW, newH)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                val newW = height
                val newH = width
                val out = IntArray(newW * newH)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[x * newW + y] = pixels[y * width + x]
                    }
                }
                Triple(out, newW, newH)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                val newW = height
                val newH = width
                val out = IntArray(newW * newH)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val dstX = height - 1 - y
                        val dstY = width - 1 - x
                        out[dstY * newW + dstX] = pixels[y * width + x]
                    }
                }
                Triple(out, newW, newH)
            }

            else -> Triple(pixels.copyOf(), width, height)
        }
    }

    /**
     * Letterbox aspect-ratio-preserving ke `targetSize x targetSize` dengan padding putih
     * `(255, 255, 255)` menghasilkan array RGB Float32 `[0..255]` untuk verifikasi paritas.
     */
    fun letterboxToRgbFloatArray(
        pixels: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        targetSize: Int = 224
    ): FloatArray {
        require(srcWidth > 0 && srcHeight > 0 && pixels.size == srcWidth * srcHeight) {
            "Dimensi sumber tidak valid."
        }

        val scale = min(
            targetSize.toFloat() / srcWidth.toFloat(),
            targetSize.toFloat() / srcHeight.toFloat()
        )
        val scaledWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (srcHeight * scale).toInt().coerceAtLeast(1)
        val offsetX = (targetSize - scaledWidth) / 2
        val offsetY = (targetSize - scaledHeight) / 2

        val output = FloatArray(targetSize * targetSize * 3) { 255f }

        for (dy in 0 until scaledHeight) {
            val sy = ((dy + 0.5f) / scaledHeight * srcHeight).toInt().coerceIn(0, srcHeight - 1)
            for (dx in 0 until scaledWidth) {
                val sx = ((dx + 0.5f) / scaledWidth * srcWidth).toInt().coerceIn(0, srcWidth - 1)
                val argb = pixels[sy * srcWidth + sx]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                val outIdx = ((offsetY + dy) * targetSize + (offsetX + dx)) * 3
                output[outIdx] = r.toFloat()
                output[outIdx + 1] = g.toFloat()
                output[outIdx + 2] = b.toFloat()
            }
        }

        return output
    }
}

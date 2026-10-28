package com.example.chilileafdisease

import java.util.Locale

/**
 * Formatter laporan hasil prediksi dan panduan penanganan awal penyakit daun cabai.
 * Digunakan bersama oleh layar inspeksi ([InspectionViewModel]) dan layar riwayat ([HistoryActivity]).
 */
object TreatmentAdvisor {

    fun formatPredictionReport(result: ChiliLeafClassifier.PredictionResult): String {
        val confidence = String.format(
            Locale.getDefault(),
            "%.2f",
            result.confidence * 100
        )

        val allScores = result.scores.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { entry ->
                val percent = String.format(
                    Locale.getDefault(),
                    "%.2f",
                    entry.value * 100
                )
                "${entry.key}: $percent%"
            }

        return buildString {
            appendLine("HASIL KLASIFIKASI")
            appendLine()
            appendLine(result.label)
            appendLine("Confidence AI: $confidence%")
            appendLine()
            appendLine("PROBABILITAS SEMUA KELAS")
            appendLine(allScores)
            appendLine()
            appendLine("PANDUAN PENANGANAN AWAL")
            appendLine()
            appendLine(getDetailedAdvice(result.label))
            appendLine()
            append(
                "Catatan: Hasil AI merupakan prediksi berdasarkan gambar, bukan diagnosis pasti."
            )
        }
    }

    fun getDetailedAdvice(label: String): String {
        val disease = normalizeLabel(label)
        return when {
            "fresh" in disease -> """
                KONDISI DAUN SEHAT

                1. Siram tanaman sesuai kebutuhan.
                2. Pastikan pencahayaan cukup.
                3. Perhatikan drainase dan nutrisi tanah.
                4. Bersihkan gulma dan daun gugur.
                5. Pantau kondisi tanaman secara berkala.

                Prediksi sehat pada satu daun tidak
                memastikan semua bagian tanaman sehat.
            """.trimIndent()

            "leaf curl" in disease -> """
                PENANGANAN AWAL DAUN KERITING

                1. Periksa hama pengisap seperti
                   kutu kebul dan thrips.
                2. Perhatikan penyiraman dan
                   stres lingkungan.
                3. Pantau perubahan pada daun muda.
                4. Bersihkan gulma di sekitar tanaman.
                5. Jika gejala meluas, konsultasikan
                   dengan petugas pertanian.

                Daun keriting tidak selalu
                disebabkan virus.
            """.trimIndent()

            "cercospora" in disease -> """
                PENANGANAN AWAL BERCAK DAUN

                1. Periksa apakah bercak menyebar
                   ke daun lain.
                2. Singkirkan daun yang rusak berat.
                3. Hindari membasahi daun berlebihan
                   saat menyiram.
                4. Tingkatkan sirkulasi udara.
                5. Jaga sanitasi alat dan lingkungan.
                6. Konsultasikan jika bercak
                   terus meluas.
            """.trimIndent()

            "anthracnose" in disease -> """
                PENANGANAN AWAL ANTRAKNOSA

                1. Periksa daun, batang, dan buah cabai.
                2. Singkirkan bagian yang rusak berat
                   atau diduga terinfeksi.
                3. Bersihkan sisa tanaman sakit
                   dari area kebun.
                4. Kurangi kelembapan berlebih
                   dan tingkatkan sirkulasi udara.
                5. Konsultasikan jika kerusakan
                   terus meluas.
            """.trimIndent()

            else -> """
                Panduan khusus belum tersedia
                untuk kelas ini.

                Periksa kondisi tanaman secara
                langsung dan konsultasikan
                dengan petugas pertanian.
            """.trimIndent()
        }
    }

    fun getShortAdvice(label: String): String {
        val normalized = normalizeLabel(label)
        val text = when {
            "fresh" in normalized -> """
                PERAWATAN DAUN SEHAT

                1. Siram sesuai kebutuhan.
                2. Pastikan pencahayaan cukup.
                3. Jaga kebersihan kebun.
                4. Periksa daun secara rutin.
            """.trimIndent()

            "leaf curl" in normalized -> """
                PENANGANAN AWAL DAUN KERITING

                1. Periksa hama pengisap pada bagian bawah daun.
                2. Periksa kondisi air dan lingkungan.
                3. Pantau daun muda.
                4. Konsultasikan jika gejala meluas.
            """.trimIndent()

            "cercospora" in normalized -> """
                PENANGANAN AWAL BERCAK DAUN

                1. Pantau bercak pada daun lain.
                2. Singkirkan daun rusak berat.
                3. Hindari membasahi daun berlebihan.
                4. Perbaiki sirkulasi udara tanaman.
            """.trimIndent()

            "anthracnose" in normalized -> """
                PENANGANAN AWAL ANTRAKNOSA

                1. Periksa daun, batang, dan buah.
                2. Singkirkan bagian rusak berat.
                3. Jaga sanitasi kebun dan peralatan.
                4. Konsultasikan jika gejala meluas.
            """.trimIndent()

            else -> "Panduan belum tersedia untuk kelas ini."
        }
        return "$text\n\nCatatan: Hasil AI adalah prediksi, bukan diagnosis pasti."
    }

    private fun normalizeLabel(label: String): String {
        return label
            .lowercase(Locale.ROOT)
            .replace("_", " ")
            .replace("-", " ")
            .trim()
    }
}

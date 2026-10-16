package com.example.chilileafdisease

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var txtHistoryCount: TextView
    private lateinit var layoutHistoryEmpty: LinearLayout
    private lateinit var historyListContainer: LinearLayout
    private lateinit var btnHistoryStartDetect: Button
    private lateinit var btnClearHistory: Button
    private lateinit var navHome: LinearLayout
    private lateinit var navDetect: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navGuide: LinearLayout

    private val primaryGreen = Color.parseColor("#2B704B")
    private val titleColor = Color.parseColor("#294C35")
    private val secondaryColor = Color.parseColor("#829184")

    private val historyPrefs by lazy {
        getSharedPreferences("chilileaf_history", MODE_PRIVATE)
    }
    private val historyKey = "detections"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        initializeViews()
        setupButtons()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        displayHistory()
    }

    private fun initializeViews() {
        txtHistoryCount = findViewById(R.id.txtHistoryCount)
        layoutHistoryEmpty = findViewById(R.id.layoutHistoryEmpty)
        historyListContainer = findViewById(R.id.historyListContainer)
        btnHistoryStartDetect = findViewById(R.id.btnHistoryStartDetect)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        navHome = findViewById(R.id.navHome)
        navDetect = findViewById(R.id.navDetect)
        navHistory = findViewById(R.id.navHistory)
        navGuide = findViewById(R.id.navGuide)
    }

    private fun setupButtons() {
        btnHistoryStartDetect.setOnClickListener { openDetectionPage() }
        btnClearHistory.setOnClickListener { confirmClearHistory() }
    }

    private fun setupBottomNavigation() {
        navHome.setOnClickListener { openHomePage() }
        navDetect.setOnClickListener { openDetectionPage() }
        navHistory.setOnClickListener { /* Halaman aktif */ }
        navGuide.setOnClickListener { openGuidePage() }
    }

    private fun openHomePage() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun openDetectionPage() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun openGuidePage() {
        startActivity(Intent(this, GuideActivity::class.java))
        finish()
    }

    private fun readHistory(): JSONArray {
        val raw = historyPrefs.getString(historyKey, "[]") ?: "[]"
        return try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }

    private fun displayHistory() {
        val history = readHistory()
        historyListContainer.removeAllViews()
        txtHistoryCount.text = history.length().toString()

        if (history.length() == 0) {
            layoutHistoryEmpty.visibility = View.VISIBLE
            historyListContainer.visibility = View.GONE
            btnClearHistory.visibility = View.GONE
            return
        }

        layoutHistoryEmpty.visibility = View.GONE
        historyListContainer.visibility = View.VISIBLE
        btnClearHistory.visibility = View.VISIBLE

        val dateFormat = SimpleDateFormat(
            "dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID")
        )

        for (index in 0 until history.length()) {
            val record = history.optJSONObject(index) ?: continue
            val label = record.optString("label", "Tidak diketahui")
            val confidenceValue = record.optDouble("confidence", 0.0)
            val confidence = if (confidenceValue.isFinite()) {
                confidenceValue.coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            val confidenceText = String.format(
                Locale.getDefault(), "%.2f%%", confidence * 100
            )
            val timestamp = record.optLong("timestamp", 0L)
            val date = if (timestamp > 0L) {
                dateFormat.format(Date(timestamp))
            } else {
                "Tanggal tidak tersedia"
            }

            // Riwayat lama dapat tidak memiliki photo_file.
            val photoFileName = record.optString("photo_file", "")
            historyListContainer.addView(
                createHistoryCard(label, confidenceText, date, photoFileName)
            )
        }
    }

    private fun createHistoryCard(
        label: String,
        confidence: String,
        date: String,
        photoFileName: String
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground("#FFFFFF", 16)
            elevation = dp(2).toFloat()
            setPadding(dp(14), dp(14), dp(14), dp(14))
            isClickable = true
            isFocusable = true
            contentDescription = "Hasil deteksi $label, confidence $confidence"
        }
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // FOTO ASLI YANG DIPINDAI, bukan gambar penyakit dari drawable.
        val photoView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(84))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBackground("#E7F2E7", 12)
            clipToOutline = true
        }

        val photoFile = if (photoFileName.isNotBlank()) {
            File(File(filesDir, "history_photos"), File(photoFileName).name)
        } else {
            null
        }

        val scannedPhoto = photoFile?.takeIf { it.isFile }?.let {
            loadThumbnail(it)
        }

        if (scannedPhoto != null) {
            photoView.setImageBitmap(scannedPhoto)
            photoView.contentDescription = "Foto daun hasil scan $label"
        } else {
            // JANGAN menampilkan ilustrasi penyakit seolah itu foto scan.
            photoView.setImageResource(android.R.drawable.ic_menu_gallery)
            photoView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            photoView.setPadding(dp(19), dp(19), dp(19), dp(19))
            photoView.imageTintList = ColorStateList.valueOf(primaryGreen)
            photoView.contentDescription = "Foto scan tidak tersedia pada catatan lama"
        }
        headerRow.addView(photoView)

        val information = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val infoParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginStart = dp(12) }
        information.addView(createText(label, 16f, titleColor, true))
        information.addView(createText(date, 12f, secondaryColor).apply {
            setPadding(0, dp(5), 0, 0)
        })
        if (scannedPhoto == null) {
            information.addView(createText(
                "Foto scan lama tidak tersedia", 11f, secondaryColor
            ).apply { setPadding(0, dp(4), 0, 0) })
        }
        headerRow.addView(information, infoParams)
        val arrow = createText("›", 28f, primaryGreen, true)
        headerRow.addView(arrow)
        card.addView(headerRow)

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#EAF0E9"))
        }
        card.addView(divider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        ).apply {
            topMargin = dp(14)
            bottomMargin = dp(12)
        })

        val confidenceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        confidenceRow.addView(createText(
            "Confidence AI", 12f, secondaryColor
        ), LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))
        confidenceRow.addView(createText(confidence, 13f, primaryGreen, true))
        card.addView(confidenceRow)

        val detailText = createText(
            getShortTreatmentAdvice(label), 13f, titleColor
        ).apply {
            background = roundedBackground("#EDF5EB", 12)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            visibility = View.GONE
        }
        card.addView(detailText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })

        card.setOnClickListener {
            val expanded = detailText.visibility == View.VISIBLE
            detailText.visibility = if (expanded) View.GONE else View.VISIBLE
            arrow.text = if (expanded) "›" else "⌄"
        }
        return card
    }

    private fun loadThumbnail(file: File): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 300 || bounds.outHeight / sample > 300) {
                sample *= 2
            }
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = sample
            })
        } catch (_: Exception) {
            null
        }
    }

    private fun getShortTreatmentAdvice(label: String): String {
        val normalized = label.lowercase(Locale.ROOT)
            .replace("_", " ").replace("-", " ")
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

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Semua Riwayat?")
            .setMessage("Semua catatan beserta foto hasil scan yang disimpan aplikasi akan dihapus. Tindakan ini tidak dapat dibatalkan.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                val cleared = historyPrefs.edit().remove(historyKey).commit()
                if (cleared) {
                    // Hanya foto di folder khusus riwayat yang dihapus.
                    val photoDirectory = File(filesDir, "history_photos")
                    photoDirectory.listFiles()?.forEach { if (it.isFile) it.delete() }
                    displayHistory()
                    Toast.makeText(this, "Riwayat berhasil dihapus", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Gagal menghapus riwayat", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun createText(value: String, size: Float, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun roundedBackground(color: String, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(color))
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

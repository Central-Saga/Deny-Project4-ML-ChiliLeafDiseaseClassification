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

    private val primaryGreen = Color.parseColor("#1E6B42")
    private val titleColor = Color.parseColor("#17281D")
    private val secondaryColor = Color.parseColor("#526558")

    private val historyRepository by lazy {
        HistoryRepository.create(this)
    }

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

    private fun displayHistory() {
        val history = historyRepository.getHistory()
        historyListContainer.removeAllViews()
        txtHistoryCount.text = history.size.toString()

        if (history.isEmpty()) {
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

        for (record in history) {
            val confidenceText = String.format(
                Locale.getDefault(), "%.2f%%", record.confidence * 100
            )
            val date = if (record.timestamp > 0L) {
                dateFormat.format(Date(record.timestamp))
            } else {
                "Tanggal tidak tersedia"
            }

            historyListContainer.addView(
                createHistoryCard(
                    record = record,
                    confidenceText = confidenceText,
                    date = date
                )
            )
        }
    }

    private fun createHistoryCard(
        record: HistoryRecord,
        confidenceText: String,
        date: String
    ): LinearLayout {
        val label = record.label
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground("#FFFFFF", 18, "#E2ECE4", 1)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            contentDescription = "Hasil deteksi $label, confidence $confidenceText"
        }
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val photoView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(84))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBackground("#EAF5EE", 14)
            clipToOutline = true
        }

        val photoFile = historyRepository.resolvePhotoFile(record.photoFileName)
        val scannedPhoto = photoFile?.let { loadThumbnail(it) }

        if (scannedPhoto != null) {
            photoView.setImageBitmap(scannedPhoto)
            photoView.contentDescription = "Foto daun hasil scan $label"
        } else {
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
            setBackgroundColor(Color.parseColor("#E6EEE8"))
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
        confidenceRow.addView(createText(confidenceText, 13f, primaryGreen, true))
        card.addView(confidenceRow)

        val detailText = createText(
            TreatmentAdvisor.getShortAdvice(label), 13f, titleColor
        ).apply {
            background = roundedBackground("#EEF6F0", 12)
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

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Semua Riwayat?")
            .setMessage("Semua catatan beserta foto hasil scan yang disimpan aplikasi akan dihapus. Tindakan ini tidak dapat dibatalkan.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                if (historyRepository.clearHistory()) {
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

    private fun roundedBackground(color: String, radius: Int, strokeColor: String? = null, strokeWidthDp: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(color))
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null && strokeWidthDp > 0) {
                setStroke(dp(strokeWidthDp), Color.parseColor(strokeColor))
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

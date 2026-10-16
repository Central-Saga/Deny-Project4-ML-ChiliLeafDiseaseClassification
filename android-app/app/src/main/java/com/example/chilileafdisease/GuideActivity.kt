package com.example.chilileafdisease

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class GuideActivity : AppCompatActivity() {

    // =====================================
    // BOTTOM NAVIGATION
    // =====================================

    private lateinit var navHome: LinearLayout
    private lateinit var navDetect: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navGuide: LinearLayout

    // =====================================
    // ON CREATE
    // =====================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Menggunakan layout Panduan
        setContentView(R.layout.activity_guide)

        initializeViews()
        setupBottomNavigation()
    }

    // =====================================
    // INISIALISASI VIEW
    // =====================================

    private fun initializeViews() {

        navHome = findViewById(R.id.navHome)

        navDetect = findViewById(R.id.navDetect)

        navHistory = findViewById(R.id.navHistory)

        navGuide = findViewById(R.id.navGuide)
    }

    // =====================================
    // BOTTOM NAVIGATION
    // =====================================

    private fun setupBottomNavigation() {

        // BERANDA
        navHome.setOnClickListener {
            openHomePage()
        }

        // DETEKSI
        navDetect.setOnClickListener {
            openDetectionPage()
        }

        // RIWAYAT
        navHistory.setOnClickListener {
            openHistoryPage()
        }

        // PANDUAN
        navGuide.setOnClickListener {
            // Sudah berada di halaman Panduan.
            // Tidak perlu membuka Activity baru.
        }
    }

    // =====================================
    // MEMBUKA HALAMAN BERANDA
    // =====================================

    private fun openHomePage() {

        val intent = Intent(
            this,
            HomeActivity::class.java
        )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        )

        startActivity(intent)

        finish()
    }

    // =====================================
    // MEMBUKA HALAMAN DETEKSI
    // =====================================

    private fun openDetectionPage() {

        val intent = Intent(
            this,
            MainActivity::class.java
        )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        )

        startActivity(intent)

        finish()
    }

    // =====================================
    // MEMBUKA HALAMAN RIWAYAT
    // =====================================

    private fun openHistoryPage() {

        val intent = Intent(
            this,
            HistoryActivity::class.java
        )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        )

        startActivity(intent)

        finish()
    }
}

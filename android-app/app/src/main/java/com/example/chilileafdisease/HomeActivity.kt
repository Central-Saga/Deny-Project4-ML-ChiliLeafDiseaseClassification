package com.example.chilileafdisease

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var btnGoDetect: Button
    private lateinit var btnGoHistory: LinearLayout
    private lateinit var btnGoGuide: LinearLayout

    private lateinit var navHome: LinearLayout
    private lateinit var navDetect: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navGuide: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_home)

        initializeViews()
        setupButtons()
        setupBottomNavigation()
    }

    private fun initializeViews() {

        btnGoDetect = findViewById(R.id.btnGoDetect)
        btnGoHistory = findViewById(R.id.btnGoHistory)
        btnGoGuide = findViewById(R.id.btnGoGuide)

        navHome = findViewById(R.id.navHome)
        navDetect = findViewById(R.id.navDetect)
        navHistory = findViewById(R.id.navHistory)
        navGuide = findViewById(R.id.navGuide)
    }

    private fun setupButtons() {

        btnGoDetect.setOnClickListener {
            openDetectionPage()
        }

        btnGoHistory.setOnClickListener {
            openHistoryPage()
        }

        btnGoGuide.setOnClickListener {
            openGuidePage()
        }
    }

    private fun setupBottomNavigation() {

        navHome.setOnClickListener {
            Toast.makeText(
                this,
                "Kamu sudah berada di Beranda",
                Toast.LENGTH_SHORT
            ).show()
        }

        navDetect.setOnClickListener {
            openDetectionPage()
        }

        navHistory.setOnClickListener {
            openHistoryPage()
        }

        navGuide.setOnClickListener {
            openGuidePage()
        }
    }

    // HALAMAN DETEKSI
    private fun openDetectionPage() {

        val intent = Intent(
            this,
            MainActivity::class.java
        )

        startActivity(intent)
    }

    // HALAMAN RIWAYAT
    private fun openHistoryPage() {

        val intent = Intent(
            this,
            HistoryActivity::class.java
        )

        startActivity(intent)
    }

    // HALAMAN PANDUAN
    private fun openGuidePage() {

        val intent = Intent(
            this,
            GuideActivity::class.java
        )

        startActivity(intent)
    }
}

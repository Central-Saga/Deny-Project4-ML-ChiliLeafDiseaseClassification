package com.example.chilileafdisease

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var imgLeafPreview: ImageView
    private lateinit var btnSelectImage: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtPredictionResult: TextView
    private lateinit var progressPrediction: ProgressBar
    private lateinit var navHome: LinearLayout
    private lateinit var navDetect: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navGuide: LinearLayout
    private lateinit var mainScrollView: ScrollView

    private val viewModel: InspectionViewModel by lazy {
        ViewModelProvider(this)[InspectionViewModel::class.java]
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.classifyImage(uri)
        } else {
            val message = "Pemilihan gambar dibatalkan."
            viewModel.onActionCancelled(message)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = viewModel.pendingCameraUri
        viewModel.pendingCameraUri = null
        if (success && uri != null) {
            viewModel.classifyImage(uri)
        } else {
            val message = "Pengambilan foto dibatalkan."
            viewModel.onActionCancelled(message)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        savedInstanceState?.getString("pending_camera_uri")?.let {
            if (viewModel.pendingCameraUri == null) {
                viewModel.pendingCameraUri = Uri.parse(it)
            }
        }
        initializeViews()
        setupDetectionButtons()
        setupBottomNavigation()
        observeUiState()
    }

    private fun initializeViews() {
        imgLeafPreview = findViewById(R.id.imgLeafPreview)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        txtStatus = findViewById(R.id.txtStatus)
        txtPredictionResult = findViewById(R.id.txtPredictionResult)
        progressPrediction = findViewById(R.id.progressPrediction)
        navHome = findViewById(R.id.navHome)
        navDetect = findViewById(R.id.navDetect)
        navHistory = findViewById(R.id.navHistory)
        navGuide = findViewById(R.id.navGuide)
        mainScrollView = findScrollView(findViewById(android.R.id.content))
            ?: error("ScrollView utama tidak ditemukan.")
    }

    private fun findScrollView(view: View): ScrollView? {
        if (view is ScrollView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findScrollView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun setupDetectionButtons() {
        btnSelectImage.setOnClickListener {
            try {
                galleryLauncher.launch("image/*")
            } catch (_: Exception) {
                val message = "Galeri tidak dapat dibuka. Silakan coba kembali."
                viewModel.onActionCancelled(message)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
        btnTakePhoto.setOnClickListener {
            try {
                cameraLauncher.launch(viewModel.createTempCameraUri(this))
            } catch (e: Exception) {
                val message = "Gagal membuka kamera. Periksa akses kamera dan coba kembali."
                viewModel.onCameraLaunchError(message, e.message ?: "Terjadi kesalahan.")
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state -> renderUiState(state) }
        }
    }

    private fun renderUiState(state: InspectionUiState) {
        when (state) {
            is InspectionUiState.Idle -> setLoading(false)
            is InspectionUiState.Loading -> {
                setLoading(true)
                txtStatus.text = state.statusMessage
                txtPredictionResult.text = ""
            }
            is InspectionUiState.Result -> {
                setLoading(false)
                state.bitmap?.let { imgLeafPreview.setImageBitmap(it) }
                txtStatus.text = state.statusMessage
                txtPredictionResult.text = state.reportText
                scrollToElement(txtPredictionResult)
            }
            is InspectionUiState.Error -> {
                setLoading(false)
                txtStatus.text = state.statusMessage
                if (state.detailMessage.isNotEmpty()) {
                    txtPredictionResult.text = state.detailMessage
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressPrediction.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSelectImage.isEnabled = !isLoading
        btnTakePhoto.isEnabled = !isLoading
    }

    private fun setupBottomNavigation() {
        navHome.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
        navDetect.setOnClickListener {
            setActiveNavigation(navDetect)
            scrollToElement(imgLeafPreview)
        }
        navHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
            finish()
        }
        navGuide.setOnClickListener {
            startActivity(Intent(this, GuideActivity::class.java))
            finish()
        }
        setActiveNavigation(navDetect)
    }

    private fun setActiveNavigation(active: View) {
        val activeColor = Color.parseColor("#2B704B")
        val inactiveColor = Color.parseColor("#87968B")
        for (item in listOf(navHome, navDetect, navHistory, navGuide)) {
            val selected = item === active
            val color = if (selected) activeColor else inactiveColor
            for (index in 0 until item.childCount) {
                when (val child = item.getChildAt(index)) {
                    is ImageView -> child.imageTintList = ColorStateList.valueOf(color)
                    is TextView -> {
                        child.setTextColor(color)
                        child.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                    }
                }
            }
        }
    }

    private fun scrollToElement(target: View) {
        mainScrollView.post {
            var top = 0
            var current: View? = target
            while (current != null && current !== mainScrollView) {
                top += current.top
                current = current.parent as? View
            }
            if (current === mainScrollView) {
                val margin = (16 * resources.displayMetrics.density).toInt()
                mainScrollView.smoothScrollTo(0, (top - margin).coerceAtLeast(0))
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_camera_uri", viewModel.pendingCameraUri?.toString())
        super.onSaveInstanceState(outState)
    }
}

// app/src/main/java/com/example/a22i1066_b_socially/HighlightViewActivity.kt
package com.example.a22i1066_b_socially

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.a22i1066_b_socially.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HighlightViewActivity : AppCompatActivity() {

    private lateinit var closeBtn: ImageView
    private lateinit var moreBtn: ImageView
    private lateinit var titleText: TextView
    private lateinit var dateText: TextView
    private lateinit var imageView: ImageView
    private lateinit var progressBars: LinearLayout

    private var highlightId = ""
    private var highlight: Highlight? = null
    private var currentImageIndex = 0

    private val TAG = "HighlightViewActivity"

    // Auto-progress timer
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private val PROGRESS_DURATION = 5000L // 5 seconds per image
    private val progressAnimators = mutableListOf<ObjectAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_highlight_view)

        highlightId = intent.getStringExtra("highlightId") ?: ""
        if (highlightId.isEmpty()) {
            finish()
            return
        }

        initViews()
        loadHighlight()
    }

    private fun initViews() {
        closeBtn = findViewById(R.id.closeBtn)
        moreBtn = findViewById(R.id.moreBtn)
        titleText = findViewById(R.id.titleText)
        dateText = findViewById(R.id.dateText)
        imageView = findViewById(R.id.highlightImageView)
        progressBars = findViewById(R.id.progressBars)

        closeBtn.setOnClickListener { finish() }
        moreBtn.setOnClickListener { showOptions() }
        imageView.setOnClickListener { nextImage() }
    }

    private fun loadHighlight() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getHighlight(highlightId)

                if (response.isSuccessful && response.body()?.success == true) {
                    val item = response.body()?.highlight
                    if (item != null) {
                        highlight = Highlight(
                            id = item.id,
                            userId = item.userId ?: item.user_id ?: "",
                            title = item.title,
                            imageUrls = item.imageUrls,
                            date = item.date
                        )
                        runOnUiThread {
                            displayHighlight()
                        }
                    } else {
                        finish()
                    }
                } else {
                    Log.e(TAG, "Failed to load highlight: ${response.body()?.error}")
                    runOnUiThread {
                        Toast.makeText(this@HighlightViewActivity, "Failed to load highlight", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading highlight", e)
                runOnUiThread {
                    Toast.makeText(this@HighlightViewActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun displayHighlight() {
        val hl = highlight ?: return

        titleText.text = hl.title

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        dateText.text = dateFormat.format(Date(hl.date * 1000)) // Convert seconds to milliseconds

        setupProgressBars(hl.imageUrls.size)
        showImage(0)
    }

    private fun setupProgressBars(count: Int) {
        progressBars.removeAllViews()
        progressAnimators.clear()

        for (i in 0 until count) {
            val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
            val params = LinearLayout.LayoutParams(0, 4)
            params.weight = 1f
            if (i > 0) params.marginStart = 8
            progressBar.layoutParams = params
            progressBar.max = 100
            progressBar.progress = 0
            progressBar.progressDrawable = resources.getDrawable(R.drawable.progress_bar_white, theme)
            progressBars.addView(progressBar)
        }
    }

    private fun showImage(index: Int) {
        val hl = highlight ?: return
        if (index >= hl.imageUrls.size) return

        currentImageIndex = index

        Glide.with(this)
            .load(hl.imageUrls[index])
            .centerCrop()
            .into(imageView)

        updateProgressBars()
        startAutoProgress()
    }

    private fun updateProgressBars() {
        // Cancel all previous animations
        progressAnimators.forEach { it.cancel() }
        progressAnimators.clear()

        for (i in 0 until progressBars.childCount) {
            val bar = progressBars.getChildAt(i) as? ProgressBar ?: continue

            when {
                i < currentImageIndex -> {
                    // Already viewed - fill completely
                    bar.progress = 100
                }
                i == currentImageIndex -> {
                    // Currently viewing - animate from 0 to 100
                    bar.progress = 0
                    val animator = ObjectAnimator.ofInt(bar, "progress", 0, 100)
                    animator.duration = PROGRESS_DURATION
                    animator.start()
                    progressAnimators.add(animator)
                }
                else -> {
                    // Not yet viewed - empty
                    bar.progress = 0
                }
            }
        }
    }

    private fun startAutoProgress() {
        stopAutoProgress()

        progressRunnable = Runnable {
            nextImage()
        }
        handler.postDelayed(progressRunnable!!, PROGRESS_DURATION)
    }

    private fun stopAutoProgress() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun nextImage() {
        val hl = highlight ?: return

        if (currentImageIndex < hl.imageUrls.size - 1) {
            showImage(currentImageIndex + 1)
        } else {
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        stopAutoProgress()
        progressAnimators.forEach { it.pause() }
    }

    override fun onResume() {
        super.onResume()
        if (highlight != null) {
            startAutoProgress()
            progressAnimators.forEach { it.resume() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoProgress()
        progressAnimators.forEach { it.cancel() }
        progressAnimators.clear()
    }

    private fun showOptions() {
        AlertDialog.Builder(this)
            .setTitle("Highlight Options")
            .setItems(arrayOf("Delete Highlight")) { _, _ ->
                confirmDelete()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete Highlight")
            .setMessage("Are you sure you want to delete this highlight?")
            .setPositiveButton("Delete") { _, _ ->
                deleteHighlight()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteHighlight() {
        val sessionManager = SessionManager(this)
        val token = "Bearer ${sessionManager.getAuthToken()}"

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.deleteHighlight(token, highlightId)

                if (response.isSuccessful && response.body()?.success == true) {
                    runOnUiThread {
                        Toast.makeText(this@HighlightViewActivity, "Highlight deleted", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    val error = response.body()?.error ?: "Failed to delete"
                    Log.e(TAG, "Delete failed: $error")
                    runOnUiThread {
                        Toast.makeText(this@HighlightViewActivity, error, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting highlight", e)
                runOnUiThread {
                    Toast.makeText(this@HighlightViewActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

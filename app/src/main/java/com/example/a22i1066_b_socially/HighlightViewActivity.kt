// app/src/main/java/com/example/a22i1066_b_socially/HighlightViewActivity.kt
package com.example.a22i1066_b_socially

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class HighlightViewActivity : AppCompatActivity() {

    private lateinit var closeBtn: ImageView
    private lateinit var moreBtn: ImageView
    private lateinit var titleText: TextView
    private lateinit var dateText: TextView
    private lateinit var imageView: ImageView
    private lateinit var progressBars: LinearLayout

    private val db = FirebaseFirestore.getInstance()
    private var highlightId = ""
    private var highlight: Highlight? = null
    private var currentImageIndex = 0

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
        db.collection("highlights").document(highlightId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    highlight = Highlight(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        title = doc.getString("title") ?: "",
                        imageUrls = doc.get("imageUrls") as? List<String> ?: emptyList(),
                        date = doc.getTimestamp("date")
                    )
                    displayHighlight()
                } else {
                    finish()
                }
            }
            .addOnFailureListener { finish() }
    }

    private fun displayHighlight() {
        val hl = highlight ?: return

        titleText.text = hl.title

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        dateText.text = hl.date?.toDate()?.let { dateFormat.format(it) } ?: ""

        setupProgressBars(hl.imageUrls.size)
        showImage(0)
    }

    private fun setupProgressBars(count: Int) {
        progressBars.removeAllViews()

        for (i in 0 until count) {
            val progressBar = View(this)
            val params = LinearLayout.LayoutParams(0, 4)
            params.weight = 1f
            if (i > 0) params.marginStart = 4
            progressBar.layoutParams = params
            progressBar.setBackgroundColor(0x70707070)
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
    }

    private fun updateProgressBars() {
        for (i in 0 until progressBars.childCount) {
            val bar = progressBars.getChildAt(i)
            bar.setBackgroundColor(
                if (i == currentImageIndex) 0xFFFFFFFF.toInt()
                else 0x70707070
            )
        }
    }

    private fun nextImage() {
        val hl = highlight ?: return

        if (currentImageIndex < hl.imageUrls.size - 1) {
            showImage(currentImageIndex + 1)
        } else {
            finish()
        }
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
        db.collection("highlights").document(highlightId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Highlight deleted", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete highlight", Toast.LENGTH_SHORT).show()
            }
    }
}

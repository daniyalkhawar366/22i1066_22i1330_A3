package com.example.a22i1066_b_socially

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import java.io.File
import java.io.FileOutputStream

class AddStoryActivity : AppCompatActivity() {

    private val TAG = "AddStoryActivity"

    private lateinit var btnPickImage: ImageView
    private lateinit var btnCenter: ImageView
    private lateinit var storyPreview: ImageView
    private lateinit var ivBack: ImageView

    private var selectedUri: Uri? = null

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // Copy gallery image to app cache so UploadStoryActivity can read it later
            val cached = copyUriToCacheFile(it)
            if (cached != null) {
                selectedUri = cached
                storyPreview.visibility = android.view.View.VISIBLE
                Glide.with(this).load(cached).centerCrop().into(storyPreview)
            } else {
                Toast.makeText(this, "Failed to prepare image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp == null) {
            Toast.makeText(this, "Camera capture failed", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val uri = saveBitmapToCacheFile(bmp)
        if (uri == null) {
            Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        selectedUri = uri
        // Go directly to upload screen with this image
        navigateToUpload(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.add_story)

        btnPickImage = findViewById(R.id.btnPickImage)
        btnCenter = findViewById(R.id.btnUploadStory)
        storyPreview = findViewById(R.id.storyPreview)
        ivBack = findViewById(R.id.ivBack)

        storyPreview.visibility = android.view.View.GONE

        btnPickImage.setOnClickListener {
            pickLauncher.launch("image/*")
        }

        // Center button: if an image already selected -> proceed to upload screen; otherwise open camera
        btnCenter.setOnClickListener {
            val uri = selectedUri
            if (uri != null) {
                navigateToUpload(uri)
            } else {
                // take picture then forward
                cameraLauncher.launch(null)
            }
        }

        ivBack.setOnClickListener {
            finish()
        }
    }

    private fun navigateToUpload(uri: Uri) {
        try {
            val intent = Intent(this, UploadStoryActivity::class.java).apply {
                putExtra("imageUri", uri.toString())
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open UploadStoryActivity", e)
            Toast.makeText(this, "Failed to open upload screen", Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }

    private fun saveBitmapToCacheFile(bitmap: Bitmap): Uri? {
        val filename = "story_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, filename)
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "saveBitmapToCacheFile error", e)
            null
        }
    }

    private fun copyUriToCacheFile(src: Uri): Uri? {
        val filename = "story_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, filename)
        return try {
            contentResolver.openInputStream(src)?.use { input ->
                FileOutputStream(file).use { out ->
                    input.copyTo(out)
                }
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "copyUriToCacheFile error", e)
            null
        }
    }
}

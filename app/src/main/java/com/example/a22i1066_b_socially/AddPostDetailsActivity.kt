package com.example.a22i1066_b_socially

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.a22i1066_b_socially.network.RetrofitClient
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.or

class AddPostDetailsActivity : AppCompatActivity() {

    private val TAG = "AddPostDetailsActivity"
    private val MAX_IMAGE_SIZE = 1920 // Max width/height in pixels
    private val COMPRESSION_QUALITY = 85 // JPEG quality (0-100)


    private lateinit var closeBtn: TextView
    private lateinit var shareBtn: TextView
    private lateinit var selectedImagesRecycler: RecyclerView
    private lateinit var captionInput: EditText
    private lateinit var progressBar: ProgressBar

    private var selectedImageUris = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.add_post_details)

        val sessionManager = SessionManager(this)
        val userId = sessionManager.getUserId()

        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        closeBtn = findViewById(R.id.closeBtn)
        shareBtn = findViewById(R.id.shareBtn)
        selectedImagesRecycler = findViewById(R.id.selectedImagesRecycler)
        captionInput = findViewById(R.id.captionInput)
        progressBar = findViewById(R.id.progressBar)

        selectedImageUris = intent.getStringArrayListExtra("selectedImages") ?: emptyList()

        if (selectedImageUris.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupSelectedImagesRecycler()

        closeBtn.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        shareBtn.setOnClickListener {
            shareBtn.isEnabled = false
            progressBar.visibility = View.VISIBLE
            uploadImagesAndCreatePost(userId)
        }
    }

    private fun setupSelectedImagesRecycler() {
        val adapter = SelectedImagesAdapter(selectedImageUris)
        selectedImagesRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        selectedImagesRecycler.adapter = adapter
    }

    private fun uploadImagesAndCreatePost(userId: String) {
        lifecycleScope.launch {
            try {
                val uploadedUrls = mutableListOf<String>()
                var uploadFailed = false

                progressBar.progress = 0
                val progressStep = 70 / selectedImageUris.size

                Log.d(TAG, "========================================")
                Log.d(TAG, "Starting upload for ${selectedImageUris.size} images")
                Log.d(TAG, "User ID: $userId")
                Log.d(TAG, "========================================")

                // Upload each image using Retrofit Multipart (same as stories)
                for ((index, uriString) in selectedImageUris.withIndex()) {
                    if (uploadFailed) break

                    try {
                        val uri = Uri.parse(uriString)
                        Log.d(TAG, "→ Uploading image ${index + 1}/${selectedImageUris.size}")
                        Log.d(TAG, "  URI: $uri")
                        Log.d(TAG, "  Scheme: ${uri.scheme}")

                        // Compress image before upload
                        Log.d(TAG, "  Compressing image...")
                        val fileBytes = compressImage(uri)

                        if (fileBytes == null) {
                            Log.e(TAG, "✗ Failed to compress image")
                            uploadFailed = true
                            break
                        }

                        // Create multipart request (same as stories)
                        val requestFile = fileBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData(
                            "file",
                            "post_${System.currentTimeMillis()}_$index.jpg",
                            requestFile
                        )
                        val userIdBody = userId.toRequestBody("text/plain".toMediaTypeOrNull())

                        Log.d(TAG, "  → Sending to server...")

                        // Upload using Retrofit (same as stories)
                        val uploadResponse = RetrofitClient.instance.uploadProfilePic(userIdBody, body)

                        Log.d(TAG, "  ← Server response code: ${uploadResponse.code()}")

                        if (uploadResponse.isSuccessful && uploadResponse.body()?.success == true) {
                            val imageUrl = uploadResponse.body()?.url
                            if (!imageUrl.isNullOrBlank()) {
                                uploadedUrls.add(imageUrl)
                                Log.d(TAG, "  ✓ SUCCESS! URL: $imageUrl")
                                progressBar.progress += progressStep
                            } else {
                                Log.e(TAG, "✗ No URL returned for image ${index + 1}")
                                uploadFailed = true
                                break
                            }
                        } else {
                            val error = uploadResponse.body()?.error ?: "Unknown error"
                            Log.e(TAG, "✗ Upload failed: $error")
                            Log.e(TAG, "  Response body: ${uploadResponse.errorBody()?.string()}")
                            uploadFailed = true
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "✗ Exception uploading image ${index + 1}: ${e.message}", e)
                        uploadFailed = true
                        break
                    }
                }

                if (uploadFailed || uploadedUrls.isEmpty()) {
                    Log.e(TAG, "========================================")
                    Log.e(TAG, "UPLOAD FAILED - uploadFailed: $uploadFailed, uploadedUrls.size: ${uploadedUrls.size}")
                    Log.e(TAG, "========================================")
                    runOnUiThread {
                        handleUploadFailure()
                    }
                    return@launch
                }

                // All images uploaded, now create the post
                Log.d(TAG, "========================================")
                Log.d(TAG, "✓ All ${uploadedUrls.size} images uploaded successfully!")
                Log.d(TAG, "→ Creating post in database...")
                Log.d(TAG, "========================================")
                progressBar.progress = 80
                createPost(userId, uploadedUrls)

            } catch (e: Exception) {
                Log.e(TAG, "✗ CRITICAL ERROR in uploadImagesAndCreatePost: ${e.message}", e)
                runOnUiThread {
                    handleUploadFailure()
                }
            }
        }
    }

    private fun compressImage(uri: Uri): ByteArray? {
        try {
            // Read the image
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode bitmap")
                return null
            }

            Log.d(TAG, "  Original size: ${originalBitmap.width}x${originalBitmap.height}")

            // Calculate new dimensions to maintain aspect ratio
            val width = originalBitmap.width
            val height = originalBitmap.height
            var newWidth = width
            var newHeight = height

            if (width > MAX_IMAGE_SIZE || height > MAX_IMAGE_SIZE) {
                val ratio = width.toFloat() / height.toFloat()
                if (width > height) {
                    newWidth = MAX_IMAGE_SIZE
                    newHeight = (MAX_IMAGE_SIZE / ratio).toInt()
                } else {
                    newHeight = MAX_IMAGE_SIZE
                    newWidth = (MAX_IMAGE_SIZE * ratio).toInt()
                }
            }

            Log.d(TAG, "  Compressed size: ${newWidth}x${newHeight}")

            // Resize bitmap
            val resizedBitmap = if (newWidth != width || newHeight != height) {
                Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            } else {
                originalBitmap
            }

            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)
            val compressedBytes = outputStream.toByteArray()

            Log.d(TAG, "  Final file size: ${compressedBytes.size / 1024} KB")

            // Clean up
            if (resizedBitmap != originalBitmap) {
                resizedBitmap.recycle()
            }
            originalBitmap.recycle()

            return compressedBytes
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image: ${e.message}", e)
            return null
        }
    }

    private fun createPost(userId: String, imageUrls: List<String>) {
        val caption = captionInput.text.toString().trim()
        val postId = "${userId}_${System.currentTimeMillis()}"
        val timestamp = System.currentTimeMillis()

        lifecycleScope.launch {
            try {
                val sessionManager = SessionManager(this@AddPostDetailsActivity)
                val token = "Bearer ${sessionManager.getAuthToken()}"

                Log.d(TAG, "Creating post with:")
                Log.d(TAG, "  Post ID: $postId")
                Log.d(TAG, "  Caption: ${if (caption.isEmpty()) "(empty)" else caption}")
                Log.d(TAG, "  Images: ${imageUrls.size}")
                imageUrls.forEachIndexed { i, url -> Log.d(TAG, "    [$i] $url") }

                val request = com.example.a22i1066_b_socially.network.CreatePostRequest(
                    postId = postId,
                    caption = caption,
                    imageUrls = imageUrls,
                    timestamp = timestamp
                )

                val response = RetrofitClient.instance.createPost(token, request)

                Log.d(TAG, "Create post response code: ${response.code()}")

                // Log the raw response body to see what's being returned
                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Error response body: $errorBody")
                } else {
                    Log.d(TAG, "Response body: ${response.body()}")
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d(TAG, "========================================")
                    Log.d(TAG, "✓✓✓ POST CREATED SUCCESSFULLY! ✓✓✓")
                    Log.d(TAG, "========================================")
                    runOnUiThread {
                        progressBar.progress = 100
                        Toast.makeText(this@AddPostDetailsActivity, "Post shared!", Toast.LENGTH_SHORT).show()
                        // Navigate to FYP instead of MyProfile to see the new post
                        val intent = Intent(this@AddPostDetailsActivity, FYPActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    }
                } else {
                    val error = response.body()?.error ?: response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "✗ Failed to create post: $error")
                    runOnUiThread {
                        Toast.makeText(this@AddPostDetailsActivity, "Post creation failed: $error", Toast.LENGTH_LONG).show()
                        handleUploadFailure()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "✗ Exception creating post: ${e.message}", e)
                runOnUiThread { handleUploadFailure() }
            }
        }
    }

    private fun handleUploadFailure() {
        progressBar.visibility = View.GONE
        shareBtn.isEnabled = true
        Toast.makeText(this, "Failed to upload images. Please try again.", Toast.LENGTH_LONG).show()
    }
}


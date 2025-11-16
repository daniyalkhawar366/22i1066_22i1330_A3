package com.example.a22i1066_b_socially

import android.app.Activity
import android.content.Intent
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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.text.get
import kotlin.toString


class AddPostDetailsActivity : AppCompatActivity() {

    private val TAG = "AddPostDetailsActivity"
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val client = OkHttpClient()

    private val CLOUDINARY_URL = "https://api.cloudinary.com/v1_1/dihbswob7/image/upload"
    private val UPLOAD_PRESET = "mobile_unsigned_preset"

    private lateinit var closeBtn: TextView
    private lateinit var shareBtn: TextView
    private lateinit var selectedImagesRecycler: RecyclerView
    private lateinit var captionInput: EditText
    private lateinit var progressBar: ProgressBar

    private var selectedImageUris = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.add_post_details)

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
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
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        shareBtn.setOnClickListener {
            shareBtn.isEnabled = false
            progressBar.visibility = View.VISIBLE
            uploadImagesAndCreatePost(uid)
        }
    }

    private fun setupSelectedImagesRecycler() {
        val adapter = SelectedImagesAdapter(selectedImageUris)
        selectedImagesRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        selectedImagesRecycler.adapter = adapter
    }

    private fun uploadImagesAndCreatePost(uid: String) {
        val uploadedUrls = mutableListOf<String>()
        val totalImages = selectedImageUris.size
        val uploadCounter = AtomicInteger(0)

        selectedImageUris.forEach { uriString ->
            try {
                val bytes = contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { it.readBytes() }

                if (bytes == null) {
                    Log.e(TAG, "Failed to read image: $uriString")
                    handleUploadFailure()
                    return@forEach
                }

                val mediaType = "image/*".toMediaTypeOrNull()
                val fileBody = bytes.toRequestBody(mediaType)
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "upload.jpg", fileBody)
                    .addFormDataPart("upload_preset", UPLOAD_PRESET)
                    .build()

                val request = Request.Builder()
                    .url(CLOUDINARY_URL)
                    .post(multipartBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Upload failed for $uriString", e)
                        runOnUiThread { handleUploadFailure() }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!it.isSuccessful) {
                                Log.e(TAG, "Upload error: ${it.code}")
                                runOnUiThread { handleUploadFailure() }
                                return
                            }

                            val bodyStr = it.body?.string().orEmpty()
                            try {
                                val json = JSONObject(bodyStr)
                                val secureUrl = json.optString("secure_url", json.optString("url"))

                                if (secureUrl.isNullOrBlank()) {
                                    Log.e(TAG, "No URL in response")
                                    runOnUiThread { handleUploadFailure() }
                                    return
                                }

                                synchronized(uploadedUrls) {
                                    uploadedUrls.add(secureUrl)
                                }

                                if (uploadCounter.incrementAndGet() == totalImages) {
                                    createPostInFirestore(uid, uploadedUrls)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Parse error", e)
                                runOnUiThread { handleUploadFailure() }
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error reading image", e)
                runOnUiThread { handleUploadFailure() }
            }
        }
    }

    private fun createPostInFirestore(uid: String, imageUrls: List<String>) {
        val caption = captionInput.text.toString().trim()
        val postId = "${uid}_${System.currentTimeMillis()}"
        val timestamp = System.currentTimeMillis()

        lifecycleScope.launch {
            try {
                val sessionManager = SessionManager(this@AddPostDetailsActivity)
                val token = "Bearer ${sessionManager.getAuthToken()}"

                val request = com.example.a22i1066_b_socially.network.CreatePostRequest(
                    postId = postId,
                    caption = caption,
                    imageUrls = imageUrls,
                    timestamp = timestamp
                )

                val response = RetrofitClient.instance.createPost(token, request)

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d(TAG, "Post created successfully")
                    runOnUiThread {
                        Toast.makeText(this@AddPostDetailsActivity, "Post shared!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@AddPostDetailsActivity, MyProfileActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    }
                } else {
                    Log.e(TAG, "Failed to create post: ${response.body()?.error}")
                    runOnUiThread { handleUploadFailure() }
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch user data", e)
                runOnUiThread { handleUploadFailure() }
            }
    }


    private fun handleUploadFailure() {
        progressBar.visibility = View.GONE
        shareBtn.isEnabled = true
        Toast.makeText(this, "Failed to upload images. Please try again.", Toast.LENGTH_LONG).show()
    }
}

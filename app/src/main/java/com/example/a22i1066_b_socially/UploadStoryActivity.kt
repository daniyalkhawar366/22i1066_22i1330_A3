package com.example.a22i1066_b_socially

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.a22i1066_b_socially.network.RetrofitClient
import com.example.a22i1066_b_socially.network.UploadStoryRequest
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class UploadStoryActivity : AppCompatActivity() {

    private val TAG = "UploadStoryActivity"

    private lateinit var sessionManager: SessionManager


    private lateinit var storyImageView: ImageView
    private lateinit var closeBtn: ImageView
    private lateinit var sendBtn: ImageView
    private lateinit var btnYourStories: LinearLayout
    private lateinit var btnCloseFriends: LinearLayout
    private lateinit var ivYourStoryAvatar: ImageView
    private lateinit var uploadProgress: ProgressBar

    private var imageUri: Uri? = null
    private var isUploading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.upload_story)

            sessionManager = SessionManager(this)

            storyImageView = findViewById(R.id.storyImage)
            closeBtn = findViewById(R.id.closebtn)
            sendBtn = findViewById(R.id.sendbtn)
            btnYourStories = findViewById(R.id.btnYourStories)
            btnCloseFriends = findViewById(R.id.btnCloseFriends)
            ivYourStoryAvatar = findViewById(R.id.ivYourStoryAvatar)
            uploadProgress = findViewById(R.id.uploadProgress)

            uploadProgress.visibility = android.view.View.GONE

            val uriStr = intent.getStringExtra("imageUri")
            Log.d(TAG, "Received imageUri: $uriStr")

            if (uriStr.isNullOrBlank()) {
                Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            try {
                imageUri = Uri.parse(uriStr)
                Log.d(TAG, "Parsed URI: $imageUri, scheme: ${imageUri?.scheme}, path: ${imageUri?.path}")
                Glide.with(this).load(imageUri).centerCrop().into(storyImageView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse or load URI", e)
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            loadProfilePic()

            closeBtn.setOnClickListener {
                if (!isUploading) {
                    finish()
                } else {
                    Toast.makeText(this, "Upload in progress...", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in onCreate", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }

        btnYourStories.setOnClickListener {
            if (isUploading) {
                Toast.makeText(this, "Upload already in progress", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            uploadStory(false)
        }

        btnCloseFriends.setOnClickListener {
            if (isUploading) {
                Toast.makeText(this, "Upload already in progress", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            uploadStory(true)
        }

        sendBtn.setOnClickListener {
            Toast.makeText(this, "Send to friends (not implemented)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadProfilePic() {
        val userId = sessionManager.getUserId() ?: return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserProfile("profile", userId)

                if (response.isSuccessful && response.body()?.success == true) {
                    val profilePicUrl = response.body()?.user?.profilePicUrl

                    runOnUiThread {
                        if (!profilePicUrl.isNullOrBlank()) {
                            Glide.with(this@UploadStoryActivity)
                                .load(profilePicUrl)
                                .circleCrop()
                                .into(ivYourStoryAvatar)
                        } else {
                            ivYourStoryAvatar.setImageResource(R.drawable.profile_pic)
                        }
                    }
                } else {
                    runOnUiThread {
                        ivYourStoryAvatar.setImageResource(R.drawable.profile_pic)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load profile pic", e)
                runOnUiThread {
                    ivYourStoryAvatar.setImageResource(R.drawable.profile_pic)
                }
            }
        }
    }

    private fun uploadStory(closeFriendsOnly: Boolean) {
        val uri = imageUri
        if (uri == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = sessionManager.getUserId()
        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        isUploading = true
        uploadProgress.visibility = android.view.View.VISIBLE
        uploadProgress.isIndeterminate = false
        uploadProgress.max = 100
        uploadProgress.progress = 0

        btnYourStories.isEnabled = false
        btnCloseFriends.isEnabled = false
        sendBtn.isEnabled = false

        // Upload image to PHP backend
        lifecycleScope.launch {
            try {
                uploadProgress.progress = 10

                Log.d(TAG, "Starting upload for URI: $uri, scheme: ${uri.scheme}")

                // Read file bytes from URI (handle both file:// and content:// URIs)
                val fileBytes = try {
                    when (uri.scheme?.lowercase()) {
                        "file" -> {
                            // For file:// URIs (camera), read directly from file
                            val path = uri.path
                            if (path.isNullOrBlank()) {
                                Log.e(TAG, "File path is null or blank")
                                null
                            } else {
                                Log.d(TAG, "Reading from file: $path")
                                val file = File(path)
                                if (file.exists()) {
                                    file.readBytes()
                                } else {
                                    Log.e(TAG, "File does not exist: $path")
                                    null
                                }
                            }
                        }
                        "content" -> {
                            // For content:// URIs (gallery), use content resolver
                            Log.d(TAG, "Reading from content resolver")
                            val inputStream = contentResolver.openInputStream(uri)
                            val bytes = inputStream?.readBytes()
                            inputStream?.close()
                            bytes
                        }
                        else -> {
                            // Try content resolver as fallback
                            Log.d(TAG, "Unknown scheme, trying content resolver")
                            val inputStream = contentResolver.openInputStream(uri)
                            val bytes = inputStream?.readBytes()
                            inputStream?.close()
                            bytes
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read file: ${e.message}", e)
                    null
                }

                if (fileBytes == null) {
                    runOnUiThread {
                        resetUploadState()
                        Toast.makeText(
                            this@UploadStoryActivity,
                            "Failed to read image",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                uploadProgress.progress = 30

                // Create multipart request
                val requestFile = RequestBody.create("image/jpeg".toMediaTypeOrNull(), fileBytes)
                val body = MultipartBody.Part.createFormData("file", "story_${System.currentTimeMillis()}.jpg", requestFile)
                val userIdBody = userId.toRequestBody("text/plain".toMediaTypeOrNull())

                uploadProgress.progress = 50

                // Upload to PHP backend
                val uploadResponse = RetrofitClient.instance.uploadProfilePic(userIdBody, body)

                uploadProgress.progress = 70

                if (uploadResponse.isSuccessful && uploadResponse.body()?.success == true) {
                    val imageUrl = uploadResponse.body()?.url

                    if (imageUrl.isNullOrBlank()) {
                        runOnUiThread {
                            resetUploadState()
                            Toast.makeText(
                                this@UploadStoryActivity,
                                "Upload failed: No URL returned",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }

                    uploadProgress.progress = 80

                    // Save story to database
                    saveStoryToBackend(userId, imageUrl, closeFriendsOnly)
                } else {
                    runOnUiThread {
                        resetUploadState()
                        Toast.makeText(
                            this@UploadStoryActivity,
                            "Upload failed: ${uploadResponse.body()?.error}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed", e)
                runOnUiThread {
                    resetUploadState()
                    Toast.makeText(
                        this@UploadStoryActivity,
                        "Upload failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun saveStoryToBackend(userId: String, imageUrl: String, closeFriendsOnly: Boolean) {
        lifecycleScope.launch {
            try {
                val token = "Bearer ${sessionManager.getAuthToken()}"
                val request = UploadStoryRequest(imageUrl, closeFriendsOnly)

                val response = RetrofitClient.instance.uploadStory(token, request)

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d(TAG, "Story saved successfully to backend")
                    runOnUiThread {
                        uploadProgress.progress = 100
                        Toast.makeText(this@UploadStoryActivity, "Story uploaded!", Toast.LENGTH_SHORT).show()

                        // Delay before navigating to allow user to see completion
                        Handler(Looper.getMainLooper()).postDelayed({
                            val intent = Intent(this@UploadStoryActivity, FYPActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(intent)
                            finish()
                        }, 500)
                    }
                } else {
                    val errorMsg = response.body()?.error ?: "Unknown error"
                    Log.e(TAG, "Failed to save story to backend: $errorMsg")
                    runOnUiThread {
                        resetUploadState()
                        Toast.makeText(
                            this@UploadStoryActivity,
                            "Failed to save story: $errorMsg",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception saving story to backend", e)
                runOnUiThread {
                    resetUploadState()
                    Toast.makeText(
                        this@UploadStoryActivity,
                        "Failed to save story: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun resetUploadState() {
        isUploading = false
        uploadProgress.visibility = android.view.View.GONE
        uploadProgress.progress = 0
        btnYourStories.isEnabled = true
        btnCloseFriends.isEnabled = true
        sendBtn.isEnabled = true
    }
}

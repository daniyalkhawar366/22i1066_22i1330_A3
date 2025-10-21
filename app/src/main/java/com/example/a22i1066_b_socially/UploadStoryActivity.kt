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
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class UploadStoryActivity : AppCompatActivity() {

    private val TAG = "UploadStoryActivity"

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val dbRealtime = FirebaseDatabase.getInstance().reference

    private val CLOUDINARY_URL = "https://api.cloudinary.com/v1_1/dihbswob7/image/upload"
    private val UPLOAD_PRESET = "mobile_unsigned_preset"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

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
        setContentView(R.layout.upload_story)

        storyImageView = findViewById(R.id.storyImage)
        closeBtn = findViewById(R.id.closebtn)
        sendBtn = findViewById(R.id.sendbtn)
        btnYourStories = findViewById(R.id.btnYourStories)
        btnCloseFriends = findViewById(R.id.btnCloseFriends)
        ivYourStoryAvatar = findViewById(R.id.ivYourStoryAvatar)
        uploadProgress = findViewById(R.id.uploadProgress)

        uploadProgress.visibility = android.view.View.GONE

        val uriStr = intent.getStringExtra("imageUri")
        if (uriStr.isNullOrBlank()) {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        imageUri = Uri.parse(uriStr)
        Glide.with(this).load(imageUri).centerCrop().into(storyImageView)

        loadProfilePic()

        closeBtn.setOnClickListener {
            if (!isUploading) {
                finish()
            } else {
                Toast.makeText(this, "Upload in progress...", Toast.LENGTH_SHORT).show()
            }
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
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val pic = doc?.getString("profilePicUrl").orEmpty()
                if (pic.isNotBlank()) {
                    Glide.with(this).load(pic).circleCrop().into(ivYourStoryAvatar)
                } else {
                    ivYourStoryAvatar.setImageResource(R.drawable.profile_pic)
                }
            }
            .addOnFailureListener {
                ivYourStoryAvatar.setImageResource(R.drawable.profile_pic)
            }
    }

    private fun uploadStory(closeFriendsOnly: Boolean) {
        val uri = imageUri
        if (uri == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
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

        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read image bytes", e)
            null
        }

        if (bytes == null) {
            resetUploadState()
            Toast.makeText(this, "Failed to read image", Toast.LENGTH_LONG).show()
            return
        }

        val progressBody = ProgressRequestBody(bytes, "image/*") { percent ->
            runOnUiThread {
                uploadProgress.progress = percent
                Log.d(TAG, "Upload progress: $percent%")
            }
        }

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "story.jpg", progressBody)
            .addFormDataPart("upload_preset", UPLOAD_PRESET)
            .build()

        val request = Request.Builder()
            .url(CLOUDINARY_URL)
            .post(multipartBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Cloudinary upload failed", e)
                runOnUiThread {
                    resetUploadState()
                    Toast.makeText(
                        this@UploadStoryActivity,
                        "Upload failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val bodyStr = it.body?.string().orEmpty()
                        Log.e(TAG, "Cloudinary error: ${it.code} body=$bodyStr")
                        runOnUiThread {
                            resetUploadState()
                            Toast.makeText(
                                this@UploadStoryActivity,
                                "Upload failed: ${it.code}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return
                    }

                    val bodyStr = it.body?.string().orEmpty()
                    try {
                        val json = JSONObject(bodyStr)
                        val secureUrl = json.optString("secure_url", json.optString("url"))
                        if (secureUrl.isNullOrBlank()) {
                            Log.e(TAG, "No secure_url in response: $bodyStr")
                            runOnUiThread {
                                resetUploadState()
                                Toast.makeText(
                                    this@UploadStoryActivity,
                                    "Upload succeeded but no URL returned",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return
                        }

                        saveStoryToFirebase(uid, secureUrl, closeFriendsOnly)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse Cloudinary response", e)
                        runOnUiThread {
                            resetUploadState()
                            Toast.makeText(
                                this@UploadStoryActivity,
                                "Upload succeeded but response invalid",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        })
    }

    private fun saveStoryToFirebase(uid: String, imageUrl: String, closeFriendsOnly: Boolean) {
        val storyId = dbRealtime.child("stories").child(uid).push().key
        if (storyId == null) {
            runOnUiThread {
                resetUploadState()
                Toast.makeText(this, "Failed to generate story ID", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val now = System.currentTimeMillis()
        val expiresAt = now + (24 * 60 * 60 * 1000) // 24 hours

        val storyData = mapOf(
            "storyId" to storyId,
            "userId" to uid,
            "imageUrl" to imageUrl,
            "uploadedAt" to now,
            "expiresAt" to expiresAt,
            "closeFriendsOnly" to closeFriendsOnly
        )

        dbRealtime.child("stories").child(uid).child(storyId).setValue(storyData)
            .addOnSuccessListener {
                Log.d(TAG, "Story saved successfully to Realtime DB")
                runOnUiThread {
                    uploadProgress.progress = 100
                    Toast.makeText(this, "Story uploaded!", Toast.LENGTH_SHORT).show()

                    // Delay before navigating to allow user to see completion
                    Handler(Looper.getMainLooper()).postDelayed({
                        val intent = Intent(this, FYPActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                        finish()
                    }, 500)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save story to Realtime DB", e)
                runOnUiThread {
                    resetUploadState()
                    Toast.makeText(this, "Failed to save story: ${e.message}", Toast.LENGTH_LONG).show()
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

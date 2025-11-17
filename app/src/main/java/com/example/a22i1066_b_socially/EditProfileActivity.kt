package com.example.a22i1066_b_socially

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.example.a22i1066_b_socially.network.RetrofitClient
import com.example.a22i1066_b_socially.network.UpdateProfileRequest
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class EditProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()

    private lateinit var closeBtn: TextView
    private lateinit var saveBtn: TextView
    private lateinit var imgEdit: ImageView
    private lateinit var changePhoto: TextView
    private lateinit var editName: EditText
    private lateinit var editUsername: EditText
    private lateinit var editWebsite: EditText
    private lateinit var editBio: EditText
    private lateinit var editEmail: EditText
    private lateinit var progressView: View
    private lateinit var contentScroll: View

    private var selectedUri: Uri? = null

    private val TAG = "EditProfileActivity"

    private val CLOUDINARY_URL = "https://api.cloudinary.com/v1_1/dihbswob7/image/upload"
    private val UPLOAD_PRESET = "mobile_unsigned_preset"

    private val client = OkHttpClient()

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            imgEdit.setImageURI(it)
            Log.d(TAG, "Image selected: $it")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.accountinfo)

        closeBtn = findViewById(R.id.closebtn)
        saveBtn = findViewById(R.id.nextbtn)
        imgEdit = findViewById(R.id.profile_image_edit)
        changePhoto = findViewById(R.id.change_photo_text)
        editName = findViewById(R.id.edit_name)
        editUsername = findViewById(R.id.edit_username)
        editWebsite = findViewById(R.id.edit_website)
        editBio = findViewById(R.id.edit_bio)
        editEmail = findViewById(R.id.edit_email)
        progressView = findViewById(R.id.progressBarEdit)
        contentScroll = findViewById(R.id.scrollArea)

        progressView.visibility = View.VISIBLE
        contentScroll.visibility = View.GONE

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        editEmail.setText(auth.currentUser?.email ?: "")
        editEmail.isEnabled = false

        // Load profile from PHP backend
        loadProfileFromBackend(uid)

        imgEdit.setOnClickListener { pickLauncher.launch("image/*") }
        changePhoto.setOnClickListener { pickLauncher.launch("image/*") }

        closeBtn.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        saveBtn.setOnClickListener {
            saveBtn.isEnabled = false
            progressView.visibility = View.VISIBLE
            val currentUid = auth.currentUser?.uid
            if (currentUid == null) {
                Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
                setResult(RESULT_CANCELED)
                finish()
                return@setOnClickListener
            }
            saveProfile(currentUid)
        }
    }

    private fun loadProfileFromBackend(uid: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserProfile(
                    userId = uid,
                    currentUserId = uid
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    val user = response.body()?.user
                    if (user == null) {
                        runOnUiThread {
                            progressView.visibility = View.GONE
                            contentScroll.visibility = View.VISIBLE
                            Toast.makeText(this@EditProfileActivity, "User not found", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    runOnUiThread {
                        val first = user.firstName.trim()
                        val last = user.lastName.trim()
                        val combined = listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ")
                        if (combined.isNotEmpty()) {
                            editName.setText(combined)
                        } else {
                            editName.setText(user.displayName)
                        }

                        editUsername.setText(user.username)
                        editWebsite.setText(user.website)
                        editBio.setText(user.bio)

                        val pic = user.profilePicUrl
                        if (pic.isNotBlank()) {
                            Glide.with(this@EditProfileActivity).load(pic).circleCrop()
                                .signature(ObjectKey(System.currentTimeMillis().toString()))
                                .into(imgEdit)
                        } else {
                            imgEdit.setImageResource(R.drawable.profileicon)
                        }

                        progressView.visibility = View.GONE
                        contentScroll.visibility = View.VISIBLE
                    }
                } else {
                    runOnUiThread {
                        progressView.visibility = View.GONE
                        contentScroll.visibility = View.VISIBLE
                        Log.e(TAG, "Failed to load profile: ${response.body()?.error}")
                        Toast.makeText(this@EditProfileActivity, "Failed to load profile", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile", e)
                runOnUiThread {
                    progressView.visibility = View.GONE
                    contentScroll.visibility = View.VISIBLE
                    Toast.makeText(this@EditProfileActivity, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveProfile(uid: String) {
        val nameRaw = editName.text.toString().trim()
        val parts = nameRaw.split("\\s+".toRegex(), 2)
        val firstName = parts.getOrNull(0).orEmpty()
        val lastName = parts.getOrNull(1).orEmpty()

        val website = editWebsite.text.toString().trim()
        val bio = editBio.text.toString().trim()

        val uri = selectedUri
        if (uri != null) {
            val bytes = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read image bytes", e)
                null
            }

            if (bytes == null) {
                progressView.visibility = View.GONE
                saveBtn.isEnabled = true
                Toast.makeText(this, "Failed to read selected image", Toast.LENGTH_LONG).show()
                return
            }

            val mediaType = "image/*".toMediaTypeOrNull()
            val fileBody = bytes.toRequestBody(mediaType)
            val multipartBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "upload.jpg", fileBody)
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
                        progressView.visibility = View.GONE
                        saveBtn.isEnabled = true
                        Toast.makeText(this@EditProfileActivity, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            val bodyStr = it.body?.string().orEmpty()
                            Log.e(TAG, "Cloudinary response error: ${it.code} body=$bodyStr")
                            runOnUiThread {
                                progressView.visibility = View.GONE
                                saveBtn.isEnabled = true
                                Toast.makeText(this@EditProfileActivity, "Upload failed: ${it.code}", Toast.LENGTH_LONG).show()
                            }
                            return
                        }

                        val bodyStr = it.body?.string().orEmpty()
                        try {
                            val json = JSONObject(bodyStr)
                            val secureUrl = json.optString("secure_url", json.optString("url"))
                            if (secureUrl.isNullOrBlank()) {
                                Log.e(TAG, "No secure_url in Cloudinary response: $bodyStr")
                                runOnUiThread {
                                    progressView.visibility = View.GONE
                                    saveBtn.isEnabled = true
                                    Toast.makeText(this@EditProfileActivity, "Upload succeeded but no URL returned", Toast.LENGTH_LONG).show()
                                }
                                return
                            }

                            performBackendUpdate(uid, firstName, lastName, nameRaw, website, bio, secureUrl)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse Cloudinary response", e)
                            runOnUiThread {
                                progressView.visibility = View.GONE
                                saveBtn.isEnabled = true
                                Toast.makeText(this@EditProfileActivity, "Upload succeeded but response invalid", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            })
        } else {
            performBackendUpdate(uid, firstName, lastName, nameRaw, website, bio, null)
        }
    }

    private fun performBackendUpdate(
        uid: String,
        firstName: String,
        lastName: String,
        displayName: String,
        website: String,
        bio: String,
        profilePicUrl: String?
    ) {
        Log.d(TAG, "Updating profile via backend for $uid")

        lifecycleScope.launch {
            try {
                val token = "Bearer ${SessionManager(this@EditProfileActivity).getAuthToken()}"
                val request = UpdateProfileRequest(
                    displayName = displayName,
                    firstName = firstName,
                    lastName = lastName,
                    bio = bio,
                    website = website,
                    profilePicUrl = profilePicUrl
                )

                val response = RetrofitClient.instance.updateProfile(token, request)

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d(TAG, "Profile update successful")
                    runOnUiThread {
                        Toast.makeText(this@EditProfileActivity, "Profile updated", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                } else {
                    Log.e(TAG, "Profile update failed: ${response.body()?.error}")
                    runOnUiThread {
                        progressView.visibility = View.GONE
                        saveBtn.isEnabled = true
                        Toast.makeText(
                            this@EditProfileActivity,
                            "Failed to save: ${response.body()?.error ?: "Unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Profile update failed", e)
                runOnUiThread {
                    progressView.visibility = View.GONE
                    saveBtn.isEnabled = true
                    Toast.makeText(
                        this@EditProfileActivity,
                        "Failed to save: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}


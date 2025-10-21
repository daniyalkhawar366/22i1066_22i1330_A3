package com.example.a22i1066_b_socially

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SignupActivity : AppCompatActivity() {

    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etDob: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etUsername: EditText
    private lateinit var ivTogglePassword: ImageView
    private lateinit var btnCreateAccount: Button
    private lateinit var ivBackArrow: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var ivProfilePic: ImageView
    private lateinit var tvChangePhoto: TextView

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private val client = OkHttpClient()

    private var isPasswordVisible = false
    private var selectedImageUri: Uri? = null

    private val TAG = "SignupActivity"
    private val CLOUDINARY_URL = "https://api.cloudinary.com/v1_1/dihbswob7/image/upload"
    private val UPLOAD_PRESET = "mobile_unsigned_preset"

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            Glide.with(this).load(it).circleCrop().into(ivProfilePic)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup)

        etFirstName = findViewById(R.id.etFirstName)
        etLastName = findViewById(R.id.etLastName)
        etDob = findViewById(R.id.etDob)
        etEmail = findViewById(R.id.etEmail)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        ivTogglePassword = findViewById(R.id.ivTogglePassword)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        ivBackArrow = findViewById(R.id.ivbackArrow)
        progressBar = findViewById(R.id.progressBar)
        ivProfilePic = findViewById(R.id.ivProfile)
        tvChangePhoto = findViewById(R.id.tvTitle)

        auth = FirebaseAuth.getInstance()

        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPassword.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etPassword.setSelection(etPassword.text.length)
        }

        ivProfilePic.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        tvChangePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        ivBackArrow.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        btnCreateAccount.setOnClickListener {
            if (!validateInputs()) return@setOnClickListener
            val first = etFirstName.text.toString().trim()
            val last = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val dob = etDob.text.toString().trim()
            val username = etUsername.text.toString().trim()
            createAccount(first, last, email, password, dob, username)
        }
    }

    private fun validateInputs(): Boolean {
        val first = etFirstName.text.toString().trim()
        val last = etLastName.text.toString().trim()
        val dob = etDob.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        val dobRegex = Regex("""\d{2}-\d{2}-\d{4}""")
        if (!dob.matches(dobRegex)) {
            etDob.error = "Enter valid date (dd-mm-yyyy)"
            etDob.requestFocus()
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Enter valid email"
            etEmail.requestFocus()
            return false
        }

        val passwordRegex = Regex("^(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]{8,}\$")
        if (!password.matches(passwordRegex)) {
            etPassword.error = "Password must be 8+ chars, include uppercase, number, special char"
            etPassword.requestFocus()
            return false
        }

        if (first.isEmpty() && last.isEmpty()) {
            etFirstName.error = "Enter at least first or last name"
            etFirstName.requestFocus()
            return false
        }

        return true
    }

    private fun createAccount(firstNameInput: String, lastNameInput: String, email: String, password: String, dob: String, usernameInput: String) {
        btnCreateAccount.isEnabled = false
        progressBar.visibility = View.VISIBLE

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    handleAuthError(task.exception)
                    return@addOnCompleteListener
                }

                val uid = auth.currentUser?.uid
                if (uid.isNullOrBlank()) {
                    showError("Failed to obtain user id")
                    return@addOnCompleteListener
                }

                // Upload image if selected, otherwise use default
                if (selectedImageUri != null) {
                    uploadImageToCloudinary(selectedImageUri!!) { imageUrl ->
                        if (imageUrl != null) {
                            saveUserToFirestore(uid, firstNameInput, lastNameInput, email, dob, usernameInput, imageUrl)
                        } else {
                            saveUserToFirestore(uid, firstNameInput, lastNameInput, email, dob, usernameInput, "")
                        }
                    }
                } else {
                    saveUserToFirestore(uid, firstNameInput, lastNameInput, email, dob, usernameInput, "")
                }
            }
    }

    private fun uploadImageToCloudinary(uri: Uri, callback: (String?) -> Unit) {
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read image", e)
            null
        }

        if (bytes == null) {
            callback(null)
            return
        }

        val mediaType = "image/*".toMediaTypeOrNull()
        val fileBody = bytes.toRequestBody(mediaType)
        val multipartBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "profile.jpg", fileBody)
            .addFormDataPart("upload_preset", UPLOAD_PRESET)
            .build()

        val request = Request.Builder()
            .url(CLOUDINARY_URL)
            .post(multipartBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Cloudinary upload failed", e)
                runOnUiThread { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e(TAG, "Upload failed: ${it.code}")
                        runOnUiThread { callback(null) }
                        return
                    }

                    try {
                        val json = JSONObject(it.body?.string() ?: "{}")
                        val secureUrl = json.optString("secure_url", json.optString("url"))
                        runOnUiThread { callback(secureUrl.takeIf { url -> url.isNotBlank() }) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse response", e)
                        runOnUiThread { callback(null) }
                    }
                }
            }
        })
    }

    private fun saveUserToFirestore(uid: String, firstName: String, lastName: String, email: String, dob: String, usernameInput: String, profilePicUrl: String) {
        val combinedName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        val displayName = if (combinedName.isNotBlank()) {
            combinedName
        } else {
            if (usernameInput.isNotBlank()) usernameInput else email.substringBefore("@").takeIf { it.isNotBlank() } ?: "user_${uid.take(6)}"
        }

        val username = if (usernameInput.isNotBlank()) usernameInput else email.substringBefore("@").takeIf { it.isNotBlank() } ?: "user_${uid.take(6)}"

        val userDoc = mapOf(
            "id" to uid,
            "uid" to uid,
            "email" to email,
            "dob" to dob,
            "firstName" to firstName,
            "lastName" to lastName,
            "displayName" to displayName,
            "username" to username,
            "profilePicUrl" to profilePicUrl,
            "bio" to "",
            "website" to "",
            "verified" to false,
            "followersCount" to 0L,
            "followingCount" to 0L,
            "postsCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("users").document(uid)
            .set(userDoc)
            .addOnSuccessListener {
                val toast = Toast.makeText(this, "Account Created", Toast.LENGTH_SHORT)
                val toastView = toast.view
                val fadeIn = AlphaAnimation(0f, 1f)
                fadeIn.duration = 400
                toastView?.startAnimation(fadeIn)
                toast.show()
                uploadPendingTokenIfNeeded(this@SignupActivity)

                Handler(Looper.getMainLooper()).postDelayed({
                    progressBar.visibility = View.GONE
                    startActivity(Intent(this, ChooseAccountActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }, 800)
            }
            .addOnFailureListener { e ->
                showError("Failed to save user: ${e.message}")
            }
    }

    private fun handleAuthError(ex: Exception?) {
        val message = when (ex) {
            is FirebaseAuthWeakPasswordException -> "Weak password: ${ex.reason ?: ex.message}"
            is FirebaseAuthInvalidCredentialsException -> "Invalid email: ${ex.message}"
            is FirebaseAuthUserCollisionException -> "This email is already registered."
            else -> "Signup failed: ${ex?.message ?: "unknown error"}"
        }
        showError(message)
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this@SignupActivity, message, Toast.LENGTH_LONG).show()
            btnCreateAccount.isEnabled = true
            progressBar.visibility = View.GONE
        }
    }
}

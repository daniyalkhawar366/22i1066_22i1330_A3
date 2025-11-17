package com.example.a22i1066_b_socially

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.a22i1066_b_socially.network.RetrofitClient
import com.example.a22i1066_b_socially.network.SignupRequest
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

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
    private lateinit var sessionManager: SessionManager

    private var isPasswordVisible = false
    private var selectedImageUri: Uri? = null

    private val TAG = "SignupActivity"

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            Glide.with(this).load(it).circleCrop().into(ivProfilePic)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup)

        sessionManager = SessionManager(this)

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
        tvChangePhoto = findViewById(R.id.tvSubtitle)

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

            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val dob = etDob.text.toString().trim()
            val username = etUsername.text.toString().trim()

            createAccount(firstName, lastName, email, password, dob, username)
        }
    }
    private fun testBackend() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.testConnection()
                if (response.isSuccessful) {
                    Toast.makeText(this@SignupActivity, "Backend connected!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SignupActivity, "Backend error: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SignupActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Test failed", e)
            }
        }
    }
    private fun validateInputs(): Boolean {
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
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

        if (firstName.isEmpty() && lastName.isEmpty()) {
            etFirstName.error = "Enter at least first or last name"
            etFirstName.requestFocus()
            return false
        }

        return true
    }

    private fun createAccount(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        dob: String,
        usernameInput: String
    ) {
        btnCreateAccount.isEnabled = false
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Upload image only if selected
                var profilePicUrl = ""
                if (selectedImageUri != null) {
                    Log.d(TAG, "Image selected, uploading...")
                    profilePicUrl = uploadImageToServer(selectedImageUri!!) ?: ""
                    if (profilePicUrl.isEmpty()) {
                        Log.w(TAG, "Image upload failed, continuing without image")
                    }
                }

                val username = if (usernameInput.isNotBlank()) usernameInput else email.substringBefore("@")

                Log.d(TAG, "Creating account for: $email, username: $username")

                val response = RetrofitClient.instance.signup(
                    SignupRequest(
                        email = email,
                        password = password,
                        firstName = firstName,
                        lastName = lastName,
                        dob = dob,
                        username = username,
                        profilePicUrl = profilePicUrl
                    )
                )

                Log.d(TAG, "Signup response code: ${response.code()}")
                Log.d(TAG, "Response successful: ${response.isSuccessful}")

                if (response.isSuccessful && response.body()?.success == true) {
                    val token = response.body()?.token ?: ""
                    val userId = response.body()?.userId ?: ""

                    Log.d(TAG, "Signup successful! UserId: $userId")
                    sessionManager.saveAuthToken(token, userId)

                    runOnUiThread {
                        Toast.makeText(this@SignupActivity, "Account Created!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@SignupActivity, FYPActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val error = response.body()?.error ?: errorBody ?: "Signup failed"
                    Log.e(TAG, "Signup failed!")
                    Log.e(TAG, "Response code: ${response.code()}")
                    Log.e(TAG, "Error body: $errorBody")
                    Log.e(TAG, "Response body: ${response.body()}")
                    showError(error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Signup error", e)
                showError("Connection failed: ${e.localizedMessage}")
            } finally {
                runOnUiThread {
                    btnCreateAccount.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        }
    }




    private suspend fun uploadImageToServer(uri: Uri): String? {
        return try {
            val file = uriToFile(uri)
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val userId = "temp_${System.currentTimeMillis()}".toRequestBody("text/plain".toMediaTypeOrNull())

            Log.d(TAG, "Uploading image: ${file.absolutePath}")

            val response = RetrofitClient.instance.uploadProfilePic(userId, body)

            file.delete()

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Upload success: ${response.body()?.url}")
                response.body()?.url
            } else {
                Log.e(TAG, "Upload failed: ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image upload exception", e)
            null
        }
    }


    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("upload", ".jpg", cacheDir)
        val outputStream = FileOutputStream(tempFile)

        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this@SignupActivity, message, Toast.LENGTH_LONG).show()
            btnCreateAccount.isEnabled = true
            progressBar.visibility = View.GONE
        }
    }
}

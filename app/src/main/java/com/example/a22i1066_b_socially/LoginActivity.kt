package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.a22i1066_b_socially.network.RetrofitClient
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var ivBackArrow: ImageView
    private lateinit var btnLogin: Button
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var ivTogglePassword: ImageView
    private lateinit var sessionManager: SessionManager

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        sessionManager = SessionManager(this)

        ivBackArrow = findViewById(R.id.ivbackArrow)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        ivTogglePassword = findViewById(R.id.ivTogglePassword)

        val signupButton = findViewById<Button>(R.id.signup_button)
        signupButton.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        ivBackArrow.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Password visibility toggle
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPassword.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etPassword.setSelection(etPassword.text.length)
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty()) {
                etEmail.error = "Email required"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                etPassword.error = "Password required"
                return@setOnClickListener
            }

            loginUser(email, password)
        }
    }

    private fun loginUser(email: String, password: String) {
        btnLogin.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.login(LoginRequest(email, password))

                if (response.isSuccessful && response.body()?.success == true) {
                    val token = response.body()?.token ?: ""
                    val userId = response.body()?.userId ?: ""

                    // Save session
                    sessionManager.saveAuthToken(token, userId)

                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Login Successful", Toast.LENGTH_SHORT).show()

                        // Navigate to FYP
                        val intent = Intent(this@LoginActivity, FYPActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                } else {
                    val error = response.body()?.error ?: "Login failed"
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, error, Toast.LENGTH_SHORT).show()
                        btnLogin.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnLogin.isEnabled = true
                }
            }
        }
    }

}

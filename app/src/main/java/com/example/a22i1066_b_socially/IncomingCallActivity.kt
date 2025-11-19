package com.example.a22i1066_b_socially

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.a22i1066_b_socially.network.RetrofitClient
import kotlinx.coroutines.launch

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var callerProfileImage: ImageView
    private lateinit var callerName: TextView
    private lateinit var callTypeText: TextView
    private lateinit var acceptButton: ImageView
    private lateinit var declineButton: ImageView
    private lateinit var sessionManager: SessionManager

    private var chatId: String = ""
    private var callId: String = ""
    private var callerUserId: String = ""
    private var callerUsername: String = ""
    private var callerProfileUrl: String = ""
    private var currentUserId: String = ""
    private var callType: String = "audio"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        sessionManager = SessionManager(this)

        callerProfileImage = findViewById(R.id.callerProfileImage)
        callerName = findViewById(R.id.callerName)
        callTypeText = findViewById(R.id.callTypeText)
        acceptButton = findViewById(R.id.acceptButton)
        declineButton = findViewById(R.id.declineButton)

        // Get intent extras
        callId = intent.getStringExtra("CALL_ID") ?: ""
        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        callerUserId = intent.getStringExtra("CALLER_USER_ID") ?: ""
        callerUsername = intent.getStringExtra("CALLER_USERNAME") ?: ""
        callerProfileUrl = intent.getStringExtra("CALLER_PROFILE_URL") ?: ""
        currentUserId = intent.getStringExtra("CURRENT_USER_ID") ?: sessionManager.getUserId().orEmpty()
        callType = intent.getStringExtra("CALL_TYPE") ?: "audio"

        // Set caller info
        callerName.text = callerUsername
        callTypeText.text = "Incoming ${if (callType == "video") "video" else "voice"} call..."

        // Load profile picture
        if (callerProfileUrl.isNotBlank()) {
            Glide.with(this)
                .load(callerProfileUrl)
                .circleCrop()
                .into(callerProfileImage)
        } else {
            // Load from PHP backend if not provided
            lifecycleScope.launch {
                try {
                    val token = sessionManager.getToken() ?: ""
                    val response = RetrofitClient.instance.getUserInfo("Bearer $token", callerUserId)

                    if (response.isSuccessful && response.body()?.success == true) {
                        val user = response.body()?.user
                        val name = user?.username?.takeIf { it.isNotBlank() } ?: callerUsername
                        val pic = user?.profile_pic_url.orEmpty()

                        callerName.text = name
                        if (pic.isNotBlank()) {
                            Glide.with(this@IncomingCallActivity)
                                .load(pic)
                                .circleCrop()
                                .into(callerProfileImage)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("IncomingCallActivity", "Error loading user info", e)
                }
            }
        }

        // Accept button
        acceptButton.setOnClickListener {
            acceptCall()
        }

        // Decline button
        declineButton.setOnClickListener {
            declineCall()
        }
    }

    private fun acceptCall() {
        // Update call status to accepted via PHP backend
        lifecycleScope.launch {
            try {
                val token = sessionManager.getToken() ?: ""
                val response = RetrofitClient.instance.updateCallStatus(
                    "Bearer $token",
                    com.example.a22i1066_b_socially.network.UpdateCallStatusRequest(
                        callId = callId,
                        status = "accepted"
                    )
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    // Start CallActivity for both audio and video calls (it handles both)
                    val intent = Intent(this@IncomingCallActivity, CallActivity::class.java).apply {
                        putExtra("CHAT_ID", chatId)
                        putExtra("CALL_ID", callId)
                        putExtra("RECEIVER_USER_ID", callerUserId)
                        putExtra("RECEIVER_USERNAME", callerUsername)
                        putExtra("RECEIVER_PROFILE_URL", callerProfileUrl)
                        putExtra("CURRENT_USER_ID", currentUserId)
                        putExtra("CALL_TYPE", callType) // "audio" or "video"
                        putExtra("IS_INCOMING", true)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@IncomingCallActivity,
                            "Failed to accept call",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("IncomingCallActivity", "Error accepting call", e)
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@IncomingCallActivity,
                        "Network error",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }
    }

    private fun declineCall() {
        // Update call status to rejected via PHP backend
        lifecycleScope.launch {
            try {
                val token = sessionManager.getToken() ?: ""
                RetrofitClient.instance.updateCallStatus(
                    "Bearer $token",
                    com.example.a22i1066_b_socially.network.UpdateCallStatusRequest(
                        callId = callId,
                        status = "rejected"
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("IncomingCallActivity", "Error declining call", e)
            }
        }
        finish()
    }
}


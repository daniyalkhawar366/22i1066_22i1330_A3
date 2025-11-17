package com.example.a22i1066_b_socially

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent

class SplashActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private val duration = 2000L // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_activity)

        progressBar = findViewById(R.id.progressBar)
        animateLoader()
    }

    private fun animateLoader() {
        val handler = Handler(Looper.getMainLooper())
        var progress = 0

        handler.post(object : Runnable {
            override fun run() {
                progress += 1
                progressBar.progress = progress
                if (progress < 100) {
                    handler.postDelayed(this, duration / 100)
                } else {
                    // Check if user is logged in using SessionManager
                    val sessionManager = SessionManager(this@SplashActivity)
                    val nextActivity = if (sessionManager.isLoggedIn()) {
                        FYPActivity::class.java
                    } else {
                        MainActivity::class.java
                    }
                    startActivity(Intent(this@SplashActivity, nextActivity))
                    finish()
                }
            }
        })
    }
}

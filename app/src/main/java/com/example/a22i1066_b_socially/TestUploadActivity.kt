package com.example.a22i1066_b_socially

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class TestUploadActivity : AppCompatActivity() {
    private val TAG = "TestUploadActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val resultText = TextView(this).apply {
            text = "Click button to test upload endpoint"
            textSize = 16f
        }

        val testButton = Button(this).apply {
            text = "Test Upload Endpoint"
            setOnClickListener {
                testUploadEndpoint(resultText)
            }
        }

        layout.addView(testButton)
        layout.addView(resultText)
        setContentView(layout)
    }

    private fun testUploadEndpoint(resultText: TextView) {
        resultText.text = "Testing..."

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://192.168.18.55/backend/api/test_upload.php")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    resultText.text = "FAILED: ${e.message}\n\nCheck your PHP server is running and IP is correct"
                    Log.e(TAG, "Connection failed", e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                runOnUiThread {
                    resultText.text = if (response.isSuccessful) {
                        "SUCCESS!\n\nResponse:\n$body"
                    } else {
                        "ERROR: ${response.code}\n\n$body"
                    }
                    Log.d(TAG, "Response: $body")
                }
            }
        })
    }
}


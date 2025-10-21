package com.example.a22i1066_b_socially

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.messaging.FirebaseMessaging
import android.util.Log
import android.widget.Toast

class DebugTokenFetcher : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("DEBUG_FCM", "FCM token: $token")
                token?.let { savePendingToken(applicationContext, it) }
                Toast.makeText(this, "Token fetched. Check logcat: DEBUG_FCM", Toast.LENGTH_SHORT).show()
            } else {
                Log.w("DEBUG_FCM", "Token fetch failed", task.exception)
                Toast.makeText(this, "Token fetch failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }
}

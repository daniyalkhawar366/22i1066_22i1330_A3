package com.example.a22i1066_b_socially

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.firebase.database.*

class CallListenerService : Service() {

    private val TAG = "CallListenerService"
    private lateinit var sessionManager: SessionManager
    private var callListener: ValueEventListener? = null
    private var callRef: DatabaseReference? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        startListeningForCalls()
    }

    private fun startListeningForCalls() {
        val currentUserId = sessionManager.getUserId() ?: return

        val database = FirebaseDatabase.getInstance()
        callRef = database.getReference("incoming_calls").child(currentUserId)

        callListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val callerId = snapshot.child("callerId").getValue(String::class.java) ?: return
                    val callerUsername = snapshot.child("callerUsername").getValue(String::class.java) ?: ""
                    val callerProfileUrl = snapshot.child("callerProfileUrl").getValue(String::class.java) ?: ""
                    val callType = snapshot.child("callType").getValue(String::class.java) ?: "audio"
                    val chatId = snapshot.child("chatId").getValue(String::class.java) ?: ""

                    Log.d(TAG, "Incoming call from $callerId")

                    // Show incoming call activity
                    val intent = Intent(this@CallListenerService, IncomingCallActivity::class.java).apply {
                        putExtra("CHAT_ID", chatId)
                        putExtra("CALLER_USER_ID", callerId)
                        putExtra("CALLER_USERNAME", callerUsername)
                        putExtra("CALLER_PROFILE_URL", callerProfileUrl)
                        putExtra("CURRENT_USER_ID", currentUserId)
                        putExtra("CALL_TYPE", callType)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)

                    // Clear the call notification
                    callRef?.removeValue()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Call listener cancelled", error.toException())
            }
        }

        callRef?.addValueEventListener(callListener!!)
        Log.d(TAG, "Started listening for calls for user: $currentUserId")
    }

    override fun onDestroy() {
        super.onDestroy()
        callListener?.let { callRef?.removeEventListener(it) }
        callListener = null
        callRef = null
    }
}


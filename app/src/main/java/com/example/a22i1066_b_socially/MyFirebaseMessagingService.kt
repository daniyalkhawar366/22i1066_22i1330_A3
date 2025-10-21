// file: `app/src/main/java/com/example/a22i1066_b_socially/MyFirebaseMessagingService.kt`
package com.example.a22i1066_b_socially

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.*

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val TAG = "MyFirebaseMsgSvc"
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM new token: $token")
        val uid = auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            uploadTokenForUser(uid, token)
        } else {
            savePendingToken(applicationContext, token)
            Log.d(TAG, "Saved FCM token locally until user signs in")
        }
    }

    private fun uploadTokenForUser(uid: String, token: String) {
        val docRef = db.collection("users").document(uid)
            .collection("fcmTokens").document(token)
        val payload = mapOf(
            "token" to token,
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        docRef.set(payload)
            .addOnSuccessListener { Log.d(TAG, "Saved FCM token for user $uid") }
            .addOnFailureListener { e -> Log.w(TAG, "Failed saving token", e) }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received: ${remoteMessage.data}")

        val data = remoteMessage.data
        val notif = remoteMessage.notification

        val type = data["type"] ?: "generic"
        val title = data["title"] ?: notif?.title ?: "App"
        val body = data["body"] ?: notif?.body ?: ""
        val chatId = data["chatId"]
        val senderId = data["senderId"]
        val targetUserId = data["targetUserId"]
        val senderProfileUrl = data["senderProfileUrl"]

        val intent = when (type) {
            "message" -> Intent(this, ChatDetailActivity::class.java).apply {
                chatId?.let { putExtra("chatId", it) }
                senderId?.let { putExtra("receiverUserId", it) }
            }
            "follow_request" -> Intent(this, ProfileActivity::class.java).apply {
                putExtra("userId", targetUserId ?: senderId)
            }
            "screenshot_alert" -> Intent(this, ChatDetailActivity::class.java).apply {
                chatId?.let { putExtra("chatId", it) }
            }
            else -> Intent(this, FYPActivity::class.java)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pending = PendingIntent.getActivity(
            this,
            Random().nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Try to load profile picture
        if (!senderProfileUrl.isNullOrBlank()) {
            try {
                val bitmap: Bitmap = Glide.with(applicationContext)
                    .asBitmap()
                    .load(senderProfileUrl)
                    .submit()
                    .get()

                NotificationHelper(this).showNotification(
                    id = Random().nextInt(),
                    title = title,
                    body = body,
                    pendingIntent = pending,
                    largeImage = bitmap
                )
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load profile image", e)
            }
        }

        // Fallback without image
        NotificationHelper(this).showNotification(
            id = Random().nextInt(),
            title = title,
            body = body,
            pendingIntent = pending
        )
    }
}

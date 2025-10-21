// file: `app/src/main/java/com/example/a22i1066_b_socially/UserTokenUploader.kt`
package com.example.a22i1066_b_socially

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

// Renamed to match calls from LoginActivity / SignupActivity
fun uploadPendingTokenIfNeeded(ctx: Context) {
    val token = getPendingToken(ctx) ?: return
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = FirebaseFirestore.getInstance()
    val docRef = db.collection("users").document(uid)
        .collection("fcmTokens").document(token)
    val payload = mapOf("token" to token, "createdAt" to Timestamp.now())
    docRef.set(payload).addOnSuccessListener {
        removePendingToken(ctx)
    }.addOnFailureListener {
        // keep token cached for retry
    }
}

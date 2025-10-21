// kotlin
package com.example.a22i1066_b_socially

import android.content.Context

private const val PREFS_NAME = "fcm_prefs"
private const val KEY_PENDING_TOKEN = "pending_token"

// Save a token locally until the user signs in
fun savePendingToken(context: Context, token: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_PENDING_TOKEN, token).apply()
}

// Retrieve the locally saved token (or null)
fun getPendingToken(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_PENDING_TOKEN, null)
}

// Public removal so other files (e.g. UserTokenUploader.kt) can clear the cached token
fun removePendingToken(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().remove(KEY_PENDING_TOKEN).apply()
}

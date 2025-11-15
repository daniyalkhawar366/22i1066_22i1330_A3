package com.example.a22i1066_b_socially

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SociallySession", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
    }

    fun saveAuthToken(token: String, userId: String) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_USER_ID, userId)
            apply()
        }
    }

    fun getAuthToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = getAuthToken() != null
}

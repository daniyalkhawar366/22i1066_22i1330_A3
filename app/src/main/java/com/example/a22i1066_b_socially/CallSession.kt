// kotlin
package com.example.a22i1066_b_socially

/**
 * Simple process-wide call session tracker.
 * Ensures state is reset when a call ends so subsequent calls behave correctly.
 */
object CallSession {
    @Volatile private var _isActive: Boolean = false
    @Volatile private var _callType: String? = null
    @Volatile private var _chatId: String? = null
    @Volatile private var _isMuted: Boolean = false

    val isActive: Boolean get() = _isActive
    val callType: String? get() = _callType
    val chatId: String? get() = _chatId
    val isMuted: Boolean get() = _isMuted

    @Synchronized
    fun start(chatId: String?, type: String) {
        _chatId = chatId
        _callType = type
        _isMuted = false
        _isActive = true
    }

    @Synchronized
    fun end() {
        _chatId = null
        _callType = null
        _isMuted = false
        _isActive = false
    }

    @Synchronized
    fun setMuted(muted: Boolean) {
        _isMuted = muted
    }
}

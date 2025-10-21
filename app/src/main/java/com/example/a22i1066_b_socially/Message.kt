package com.example.a22i1066_b_socially


data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = 0L,
    val imageUrls: List<String> = emptyList(),
    val type: String = "text",
    val postId: String? = null  // Add this
)



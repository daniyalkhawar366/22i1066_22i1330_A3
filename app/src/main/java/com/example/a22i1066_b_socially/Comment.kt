package com.example.a22i1066_b_socially

data class Comment(
    val commentId: String = "",
    val userId: String = "",
    val username: String = "",
    val profilePicUrl: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

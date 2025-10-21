package com.example.a22i1066_b_socially

import com.google.firebase.Timestamp

data class Post(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val profilePicUrl: String = "",
    val imageUrls: List<String> = emptyList(),
    val caption: String = "",
    val timestamp: Timestamp? = null,
    var likesCount: Long = 0L,
    var commentsCount: Long = 0L,
    var isLikedByCurrentUser: Boolean = false
)

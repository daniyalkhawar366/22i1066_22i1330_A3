package com.example.a22i1066_b_socially.network

import com.example.a22i1066_b_socially.User

data class UserResponse(
    val success: Boolean,
    val user: User? = null,
    val error: String? = null
)

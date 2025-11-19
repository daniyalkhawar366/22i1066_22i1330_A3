// app/src/main/java/com/example/a22i1066_b_socially/Highlight.kt
package com.example.a22i1066_b_socially

data class Highlight(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val imageUrls: List<String> = emptyList(),
    val date: Long = 0 // Unix timestamp in seconds
)

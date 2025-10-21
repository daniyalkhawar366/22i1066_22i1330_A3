// kotlin
package com.example.a22i1066_b_socially

import org.json.JSONException
import org.json.JSONObject

fun parseJsonOrNull(text: String?): JSONObject? {
    if (text.isNullOrBlank()) return null
    val trimmed = text.trim()
    if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return null
    return try {
        JSONObject(trimmed)
    } catch (ex: JSONException) {
        null
    }
}

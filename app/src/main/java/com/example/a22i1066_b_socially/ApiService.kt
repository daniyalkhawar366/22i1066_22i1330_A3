package com.example.a22i1066_b_socially.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

data class SignupRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val dob: String,
    val username: String,
    val profilePicUrl: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val success: Boolean,
    val token: String?,
    val userId: String?,
    val error: String?
)

data class UploadResponse(
    val success: Boolean,
    val url: String?,
    val error: String?
)

interface ApiService {
    @GET("test.php")
    suspend fun testConnection(): Response<TestResponse>

    data class TestResponse(
        val success: Boolean,
        val message: String,
        val time: String
    )

    @POST("auth.php?action=signup")
    suspend fun signup(@Body request: SignupRequest): Response<AuthResponse>

    @POST("auth.php?action=login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @Multipart
    @POST("upload.php")
    suspend fun uploadProfilePic(
        @Part("user_id") userId: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>
}

package com.example.a22i1066_b_socially.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    const val BASE_URL = "http://192.168.18.55/backend/api/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            android.util.Log.d("RetrofitClient", "→ REQUEST: ${request.method} ${request.url}")

            val response = chain.proceed(request)

            // Log raw response for debugging
            val responseBody = response.body
            val source = responseBody?.source()
            source?.request(Long.MAX_VALUE)
            val buffer = source?.buffer

            val bodyString = buffer?.clone()?.readUtf8() ?: ""
            android.util.Log.d("RetrofitClient", "← RESPONSE (${response.code}): ${request.url}")
            android.util.Log.d("RetrofitClient", "   Body: ${bodyString.take(500)}${if(bodyString.length > 500) "..." else ""}")

            response
        }
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()


    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

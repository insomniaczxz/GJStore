package com.example.gjstore.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.gjstore.BuildConfig

object RetrofitClient {
    // Corrected BASE_URL: Must end with a slash, and we move 'exec' to the endpoint definition
    private const val BASE_URL = "https://script.google.com/macros/s/AKfycbwTzwznKeSAjM6qtnBKQIScrsIOIiUkQ3SUSZxbxlQ2DLANFP8Q8bNm18qr0gnFvWSO/"

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    val apiService: SheetsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SheetsApiService::class.java)
    }
}

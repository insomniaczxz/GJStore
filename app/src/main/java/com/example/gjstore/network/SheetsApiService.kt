package com.example.gjstore.network

import retrofit2.Response
import retrofit2.http.*

interface SheetsApiService {
    @GET("exec")
    suspend fun readSheet(
        @Query("sheetName") sheetName: String,
        @Query("action") action: String = "read"
    ): Response<List<List<String>>>

    @POST("exec")
    suspend fun modifySheet(
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Response<okhttp3.ResponseBody>
}
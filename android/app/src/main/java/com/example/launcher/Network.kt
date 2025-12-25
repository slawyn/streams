package com.example.launcher

import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

interface ApiService {
    @GET("api/streams")
    suspend fun getStreams(): List<StreamEntry>
}

data class Stream(
    val id: String,
    val link: String,
    val available: Boolean
)

data class StreamEntry(
    val logo: String,
    val group: String,
    val name: String,
    val streams: List<Stream>
)

object RetrofitClient {
    private var baseUrl: String = "http://192.168.0.108:80"
    fun setBaseUrl(url: String) {
        baseUrl = url
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
    fun getBaseUrl(): String {
        return baseUrl
    }
}
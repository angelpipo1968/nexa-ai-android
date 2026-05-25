package com.nexa.ai.tutorial.network

import com.nexa.ai.tutorial.data.ChatRequest
import com.nexa.ai.tutorial.data.ChatResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

object RetrofitInstance {
    private const val BASE_URL = "https://api.openai.com/v1/" 

    val api: OpenAiApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiApi::class.java)
    }
}

interface OpenAiApi {
    @POST("chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): ChatResponse
}

package com.nexa.ai.data.remote

import com.nexa.ai.data.remote.dto.VideoPredictionRequest
import com.nexa.ai.data.remote.dto.VideoPredictionResponse
import retrofit2.http.*

interface ReplicateApi {
    @POST("v1/predictions")
    suspend fun createPrediction(@Body request: VideoPredictionRequest): VideoPredictionResponse

    @GET("v1/predictions/{id}")
    suspend fun getPrediction(@Path("id") id: String): VideoPredictionResponse
}

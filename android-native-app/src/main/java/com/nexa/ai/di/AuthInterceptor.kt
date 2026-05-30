package com.nexa.ai.di

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    // Inyectar desde DataStore o BuildConfig
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Obtener API key de forma segura (DataStore, EncryptedSharedPreferences, etc.)
        val apiKey = getApiKey()
        
        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .build()
        
        return chain.proceed(authenticatedRequest)
    }

    private fun getApiKey(): String {
        // TODO: Obtener de almacenamiento seguro
        // No hardcodear nunca la API key
        return ""
    }
}

package com.nexa.ai.di

import android.content.Context
import com.nexa.ai.data.local.ApiKeyManager
import com.nexa.ai.data.remote.LiteLLMApi
import com.nexa.ai.data.remote.NexaMediaApi
import com.nexa.ai.data.remote.OpenAiApi
import com.nexa.ai.data.remote.ReplicateApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideApiKeyManager(@ApplicationContext context: Context): ApiKeyManager {
        return ApiKeyManager(context)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(apiKeyManager: ApiKeyManager): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            val url = originalRequest.url.toString()
            
            val key = if (url.contains("replicate.com")) {
                runBlocking { apiKeyManager.getReplicateKey().firstOrNull() ?: "" } // Token para Replicate
            } else {
                runBlocking { apiKeyManager.getOpenAiKey().firstOrNull() ?: "" } // Bearer para OpenAI
            }

            val requestBuilder = originalRequest.newBuilder()
            if (url.contains("replicate.com")) {
                requestBuilder.header("Authorization", "Token $key")
            } else {
                requestBuilder.header("Authorization", "Bearer $key")
            }
            
            chain.proceed(requestBuilder.build())
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // Las APIs de imagen/video son lentas
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAiApi(okHttpClient: OkHttpClient): OpenAiApi {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiApi::class.java)
    }

    @Provides
    @Singleton
    fun provideReplicateApi(okHttpClient: OkHttpClient): ReplicateApi {
        return Retrofit.Builder()
            .baseUrl("https://api.replicate.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ReplicateApi::class.java)
    }

    /**
     * Provides an OkHttpClient specifically for local LiteLLM.
     * No auth interceptor needed — LiteLLM on local network doesn't require API keys.
     * Longer timeouts for VLM inference which can be slow on first load.
     */
    @Provides
    @Named("litellm")
    @Singleton
    fun provideLiteLLMOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)   // VLM inference can be slow
            .writeTimeout(60, TimeUnit.SECONDS)    // Large base64 images
            .build()
    }

    /**
     * Provides LiteLLMApi with a dynamic base URL.
     * Default: http://192.168.1.50:4000/
     *
     * IMPORTANT: Always connect to port 4000 (LiteLLM router), NOT:
     * - Port 8002 (vLLM internal - no fallback/router/balance)
     * - Port 3000 (Next.js web UI)
     */
    @Provides
    @Singleton
    fun provideLiteLLMApi(@Named("litellm") okHttpClient: OkHttpClient): LiteLLMApi {
        return Retrofit.Builder()
            .baseUrl("http://192.168.1.50:4000/")  // Default; can be updated dynamically
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LiteLLMApi::class.java)
    }

    /**
     * Provides NexaMediaApi for FREE image/video generation.
     * Connects to Nexa server which uses z-ai-web-dev-sdk (no API keys needed).
     * Supports both local (http://192.168.50.158:3000/) and cloud (https://nexa-ai.dev/).
     */
    @Provides
    @Singleton
    fun provideNexaMediaApi(): NexaMediaApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)  // Image/video gen can take time
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://nexa-ai.dev/")  // Cloud endpoint (always available)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NexaMediaApi::class.java)
    }
}

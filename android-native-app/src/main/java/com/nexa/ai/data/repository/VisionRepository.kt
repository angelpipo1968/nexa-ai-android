package com.nexa.ai.data.repository

import android.content.Context
import android.net.Uri
import com.nexa.ai.data.remote.OpenAiApi
import com.nexa.ai.data.remote.dto.*
import com.nexa.ai.util.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class VisionRepository @Inject constructor(
    private val api: OpenAiApi,
    @ApplicationContext private val context: Context
) {
    suspend fun describeImage(uri: Uri, userPrompt: String = "¿Qué hay en esta imagen?"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base64 = ImageUtils.uriToBase64(context, uri)
            val dataUri = "data:image/jpeg;base64,$base64"

            val request = VisionRequest(
                messages = listOf(
                    VisionMessage(
                        content = listOf(
                            ContentPart(type = "text", text = userPrompt),
                            ContentPart(type = "image_url", image_url = ImageUrlDto(url = dataUri))
                        )
                    )
                )
            )
            val response = api.analyzeImage(request)
            Result.success(response.choices.first().message.content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

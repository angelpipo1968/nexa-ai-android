package com.nexa.ai.tutorial.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.BuildConfig
import com.nexa.ai.tutorial.data.ChatRequest
import com.nexa.ai.tutorial.data.Message
import com.nexa.ai.tutorial.network.RetrofitInstance
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    // Lista observable de mensajes para la UI
    private val _messages = MutableLiveData<MutableList<Message>>()
    val messages: LiveData<MutableList<Message>> = _messages

    // Estado para saber si está cargando
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        // Mensaje de bienvenida inicial
        _messages.value = mutableListOf(Message("system", "Eres un asistente útil."))
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        // 1. Añadir mensaje del usuario a la lista
        val currentList = _messages.value ?: mutableListOf()
        currentList.add(Message("user", userText))
        _messages.value = currentList

        // 2. Mostrar carga
        _isLoading.value = true

        // 3. Llamada al API (Coroutines)
        viewModelScope.launch {
            try {
                // Preparamos la petición
                val request = ChatRequest(
                    model = "gpt-3.5-turbo",
                    messages = currentList
                )

                // Llamamos a Retrofit usando la API Key segura
                val authHeader = "Bearer ${BuildConfig.API_KEY}"
                val response = RetrofitInstance.api.getChatCompletion(authHeader, request)

                // 4. Procesar respuesta
                val reply = response.choices.first().message
                val updatedList = _messages.value ?: mutableListOf()
                updatedList.add(reply)
                _messages.value = updatedList

            } catch (e: Exception) {
                e.printStackTrace()
                // Agregar un mensaje de error visual para el usuario
                val updatedList = _messages.value ?: mutableListOf()
                updatedList.add(Message("system", "Error: No se pudo obtener respuesta de la API. Verifica tu clave API y conexión a internet."))
                _messages.value = updatedList
            } finally {
                _isLoading.value = false
            }
        }
    }
}

package com.nexa.ai.ui.vision

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.BuildConfig
import com.nexa.ai.data.repository.VisionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VisionState(
    val isLoading: Boolean = false,
    val imageUri: Uri? = null,
    val question: String = "¿Qué hay en esta imagen? Describe detalladamente.",
    val result: String? = null,
    val error: String? = null
)

@HiltViewModel
class VisionViewModel @Inject constructor(
    private val repo: VisionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VisionState())
    val state: StateFlow<VisionState> = _state

    // StringBuilder para evitar concatenaciones costosas en streams largos
    private val streamBuffer = StringBuilder()

    fun onImageSelected(uri: Uri) {
        _state.update { it.copy(imageUri = uri, result = null, error = null) }
    }

    fun onQuestionChanged(question: String) {
        _state.update { it.copy(question = question) }
    }

    fun analyze(baseUrl: String = BuildConfig.API_BASE_URL) {
        val uri = _state.value.imageUri ?: return
        val question = _state.value.question

        viewModelScope.launch {
            streamBuffer.clear()
            _state.update { it.copy(isLoading = true, error = null, result = "") }

            try {
                repo.describeImageStream(
                    uri = uri,
                    userPrompt = question,
                    baseUrl = baseUrl
                ).collect { chunk ->
                    streamBuffer.append(chunk)
                    // update() es atómico — evita race conditions con streams rápidos
                    _state.update { it.copy(result = streamBuffer.toString()) }
                }
                _state.update { it.copy(isLoading = false) }

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error desconocido"
                    )
                }
            }
        }
    }
}

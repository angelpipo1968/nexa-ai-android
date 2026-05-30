package com.nexa.ai.ui.videoggen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoGenState(val isLoading: Boolean = false, val statusText: String = "", val videoUrl: String? = null, val error: String? = null)

@HiltViewModel
class VideoGenViewModel @Inject constructor(private val repo: VideoRepository) : ViewModel() {
    private val _state = MutableStateFlow(VideoGenState())
    val state: StateFlow<VideoGenState> = _state

    fun generate(prompt: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, statusText = "Iniciando generación...", videoUrl = null, error = null)
            repo.generateVideo(prompt).onSuccess { 
                _state.value = _state.value.copy(isLoading = false, statusText = "¡Video listo!", videoUrl = it) 
            }.onFailure { 
                _state.value = _state.value.copy(isLoading = false, statusText = "", error = it.message) 
            }
        }
    }
}

package com.nexa.ai.ui.imagegen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.data.repository.ImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageGenState(val isLoading: Boolean = false, val prompt: String = "", val imageUrl: String? = null, val error: String? = null)

@HiltViewModel
class ImageGenViewModel @Inject constructor(private val repo: ImageRepository) : ViewModel() {
    private val _state = MutableStateFlow(ImageGenState())
    val state: StateFlow<ImageGenState> = _state

    fun onPromptChange(p: String) { _state.value = _state.value.copy(prompt = p) }
    
    fun generate() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, imageUrl = null, error = null)
            repo.generateImage(_state.value.prompt).onSuccess { _state.value = _state.value.copy(isLoading = false, imageUrl = it) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }
}

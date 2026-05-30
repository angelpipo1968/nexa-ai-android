package com.nexa.ai.ui.vision

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexa.ai.data.repository.VisionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VisionState(val isLoading: Boolean = false, val imageUri: Uri? = null, val result: String? = null, val error: String? = null)

@HiltViewModel
class VisionViewModel @Inject constructor(private val repo: VisionRepository) : ViewModel() {
    private val _state = MutableStateFlow(VisionState())
    val state: StateFlow<VisionState> = _state

    fun onImageSelected(uri: Uri) { _state.value = _state.value.copy(imageUri = uri, result = null, error = null) }

    fun analyze() {
        val uri = _state.value.imageUri ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repo.describeImage(uri).onSuccess { _state.value = _state.value.copy(isLoading = false, result = it) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }
        }
    }
}

package com.nexa.ai.ui.imagegen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@Composable
fun ImageGenScreen(viewModel: ImageGenViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = state.prompt, onValueChange = { viewModel.onPromptChange(it) }, label = { Text("Describe la imagen") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.generate() }, enabled = !state.isLoading && state.prompt.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            if (state.isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary) else Text("Crear Foto 🎨")
        }
        
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        
        state.imageUrl?.let {
            Spacer(modifier = Modifier.height(16.dp))
            AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().height(350.dp), contentScale = ContentScale.Fit)
        }
    }
}

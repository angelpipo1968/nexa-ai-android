package com.nexa.ai.ui.vision

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@Composable
fun VisionScreen(viewModel: VisionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.onImageSelected(it) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = { picker.launch("image/*") }) { Text("Seleccionar Imagen") }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        state.imageUri?.let {
            AsyncImage(model = it, contentDescription = null, modifier = Modifier.height(250.dp).fillMaxWidth(), contentScale = ContentScale.Fit)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.analyze() }, enabled = !state.isLoading) {
                if (state.isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                else Text("Analizar con IA 👁️")
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        
        state.result?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(it, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

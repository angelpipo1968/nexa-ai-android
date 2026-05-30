package com.nexa.ai.ui.videoggen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun VideoGenScreen(viewModel: VideoGenViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var prompt by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Describe el video") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.generate(prompt) }, enabled = !state.isLoading && prompt.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            if (state.isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary) else Text("Crear Video 🎬")
        }
        
        if (state.statusText.isNotBlank()) Text(state.statusText)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        
        // Reproductor de Video con ExoPlayer
        state.videoUrl?.let { url ->
            Spacer(modifier = Modifier.height(16.dp))
            val context = androidx.compose.ui.platform.LocalContext.current
            val exoPlayer = remember(url) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                }
            }
            
            DisposableEffect(exoPlayer) {
                onDispose {
                    exoPlayer.release()
                }
            }

            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply { player = exoPlayer }
                },
                modifier = Modifier.fillMaxWidth().height(250.dp)
            )
        }
    }
}

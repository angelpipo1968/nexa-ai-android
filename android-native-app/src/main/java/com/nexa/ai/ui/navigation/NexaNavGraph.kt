package com.nexa.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexa.ai.ui.vision.VisionScreen
import com.nexa.ai.ui.imagegen.ImageGenScreen
import com.nexa.ai.ui.videoggen.VideoGenScreen

@Composable
fun NexaNavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "vision") {
        composable("vision") { VisionScreen() }
        composable("image_gen") { ImageGenScreen() }
        composable("video_gen") { VideoGenScreen() }
    }
}

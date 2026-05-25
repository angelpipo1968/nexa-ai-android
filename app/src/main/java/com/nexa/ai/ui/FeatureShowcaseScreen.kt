package com.nexa.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.ml.EnhancedEmotionAnalyzer
import com.nexa.ai.ml.EmotionProfile
import com.nexa.ai.memory.EpisodicMemoryManager
import com.nexa.ai.memory.MemoryType
import com.nexa.ai.web.WebSearchManager
import com.nexa.ai.web.SearchResult
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureShowcaseScreen() {
    val coroutineScope = rememberCoroutineScope()
    
    // Managers
    val emotionAnalyzer = remember { EnhancedEmotionAnalyzer() }
    val memoryManager = remember { EpisodicMemoryManager() }
    val searchManager = remember { WebSearchManager() }

    // State
    var textInput by remember { mutableStateOf("") }
    var emotionProfile by remember { mutableStateOf<EmotionProfile?>(null) }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var memoryText by remember { mutableStateOf("") }
    var memoriesList by remember { mutableStateOf(memoryManager.queryMemories(com.nexa.ai.memory.MemoryQuery())) }

    val sessionId = remember { UUID.randomUUID().toString() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NexaIA Advanced Features") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Text Input Section
            OutlinedTextField(
                value = textInput,
                onValueChange = { 
                    textInput = it 
                    if (it.isNotEmpty()) {
                        emotionProfile = emotionAnalyzer.analyzeEmotion(it)
                    } else {
                        emotionProfile = null
                    }
                },
                label = { Text("Type something to analyze") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // Emotion Analysis Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Emotion Analysis", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (emotionProfile != null) {
                        val profile = emotionProfile!!
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.emoji, fontSize = 48.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Primary: ${profile.primaryEmotion.displayName}", fontWeight = FontWeight.Bold)
                                Text("Confidence: ${(profile.confidence * 100).toInt()}%")
                                Text("Valence: ${profile.valence}")
                                Text("Suggested Tone: ${profile.suggestedTone}")
                            }
                        }
                    } else {
                        Text("Type above to see emotion analysis.", color = Color.Gray)
                    }
                }
            }

            // Web Search Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Web Search Integration", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                isSearching = true
                                coroutineScope.launch {
                                    searchResults = searchManager.searchWeb(textInput, maxResults = 3)
                                    isSearching = false
                                }
                            }
                        },
                        enabled = textInput.isNotBlank() && !isSearching
                    ) {
                        Text(if (isSearching) "Searching..." else "Search Web for Input")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (searchResults.isNotEmpty()) {
                        searchResults.forEach { result ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(result.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(result.snippet, fontSize = 12.sp)
                            }
                        }
                    } else if (!isSearching) {
                        Text("No results yet.", color = Color.Gray)
                    }
                }
            }

            // Episodic Memory Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Episodic Memory", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = memoryText,
                        onValueChange = { memoryText = it },
                        label = { Text("Fact to remember") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            if (memoryText.isNotBlank()) {
                                memoryManager.storeMemory(
                                    sessionId = sessionId,
                                    type = MemoryType.FACT,
                                    content = memoryText,
                                    importance = 0.8f
                                )
                                memoriesList = memoryManager.queryMemories(com.nexa.ai.memory.MemoryQuery())
                                memoryText = ""
                            }
                        },
                        enabled = memoryText.isNotBlank()
                    ) {
                        Text("Save Memory")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (memoriesList.isNotEmpty()) {
                        Text("Current Memories:", fontWeight = FontWeight.SemiBold)
                        memoriesList.forEach { memory ->
                            Text("• ${memory.summary}", fontSize = 14.sp)
                        }
                    } else {
                        Text("No memories saved.", color = Color.Gray)
                    }
                }
            }
        }
    }
}

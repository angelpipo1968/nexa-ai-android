package com.nexa.ai.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.viewmodel.AppLanguage

// ═══════════════════════════════════════
//  LOTTERY SCREEN
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryScreen(
    language: AppLanguage,
    isDarkTheme: Boolean,
    onBack: () -> Unit
) {
    var selectedGame by remember { mutableStateOf("melate") }
    var isLoading by remember { mutableStateOf(false) }
    var resultNumbers by remember { mutableStateOf<List<String>>(emptyList()) }
    var generatedTickets by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var lastDrawInfo by remember { mutableStateOf<String?>(null) }
    var nextDrawInfo by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(0) } // 0=results, 1=generate

    val games = listOf(
        "melate" to "🇲🇽 Melate",
        "melate_retro" to "🇲🇽 Melate Retro",
        "chispazo" to "🇲🇽 Chispazo",
        "euromillions" to "🇪🇸 EuroMillones",
        "primitiva" to "🇪🇸 La Primitiva",
        "powerball" to "🇺🇸 Powerball",
        "megamillions" to "🇺🇸 Mega Millions",
        "baloto" to "🇨🇴 Baloto"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDarkTheme) Color(0xFF050508) else Color(0xFFF8F9FC)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                            .background(NexaAccent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center) { Text("🎰", fontSize = 16.sp) }
                        Text("Lotería", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            // Game selector
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(games) { (key, label) ->
                    val selected = selectedGame == key
                    FilterChip(
                        selected = selected,
                        onClick = { selectedGame = key; resultNumbers = emptyList(); generatedTickets = emptyList() },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexaAccent.copy(alpha = 0.15f),
                            selectedLabelColor = NexaAccent
                        ),
                        border = if (selected) BorderStroke(1.dp, NexaAccent.copy(alpha = 0.3f))
                        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tabs: Results / Generate
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabButton(
                    text = "📊 Resultados",
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    text = "🎲 Generar",
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeTab == 0) {
                    // Results tab
                    item {
                        ActionButton(
                            text = "Ver último resultado",
                            icon = Icons.Default.Refresh,
                            isLoading = isLoading,
                            onClick = {
                                isLoading = true; errorMessage = null
                                // TODO: Call /api/lottery?action=results&game=$selectedGame
                                // For now, show placeholder
                                isLoading = false
                                resultNumbers = listOf("07", "12", "23", "34", "41", "52")
                                lastDrawInfo = "Sorteo: 13 Mayo 2026"
                            }
                        )
                    }

                    if (errorMessage != null) {
                        item {
                            ErrorCard(errorMessage!!)
                        }
                    }

                    if (resultNumbers.isNotEmpty()) {
                        item {
                            ResultCard(
                                numbers = resultNumbers,
                                drawInfo = lastDrawInfo,
                                nextDraw = nextDrawInfo
                            )
                        }
                    }
                } else {
                    // Generate tab
                    item {
                        ActionButton(
                            text = "Generar 5 boletos",
                            icon = Icons.Default.Casino,
                            isLoading = isLoading,
                            onClick = {
                                isLoading = true; errorMessage = null
                                // TODO: Call /api/lottery?action=tickets&game=$selectedGame&tickets=5
                                // For now, generate locally
                                isLoading = false
                                generatedTickets = (1..5).map {
                                    (1..6).map { (1..50).random().toString().padStart(2, '0') }
                                }
                            }
                        )
                    }

                    if (errorMessage != null) {
                        item {
                            ErrorCard(errorMessage!!)
                        }
                    }

                    items(generatedTickets.size) { index ->
                        TicketCard(
                            ticketNumber = index + 1,
                            numbers = generatedTickets[index]
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════
//  COMPONENTS
// ═══════════════════════════════════════

@Composable
private fun TabButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) NexaAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = if (selected) BorderStroke(1.dp, NexaAccent.copy(alpha = 0.3f))
        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) NexaAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NexaAccent),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
        } else {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ResultCard(numbers: List<String>, drawInfo: String?, nextDraw: String?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (drawInfo != null) {
                Text(drawInfo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }

            // Number balls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                numbers.forEach { num ->
                    Ball(number = num)
                }
            }

            if (nextDraw != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                Text("Próximo sorteo: $nextDraw", fontSize = 12.sp, color = NexaAccent.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun Ball(number: String) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(NexaAccent.copy(alpha = 0.2f), NexaAccent.copy(alpha = 0.05f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = NexaAccent
        )
    }
}

@Composable
private fun TicketCard(ticketNumber: Int, numbers: List<String>) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Boleto #$ticketNumber",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NexaAccent.copy(alpha = 0.6f)
                )
                Icon(Icons.Default.Casino, null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                numbers.forEach { num ->
                    Ball(number = num)
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.06f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp))
            Text(message, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
}

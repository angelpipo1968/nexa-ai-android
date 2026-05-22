package com.nexa.ai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.nexa.ai.BuildConfig
import com.nexa.ai.data.*
import com.nexa.ai.ui.theme.NexaAccent
import com.nexa.ai.viewmodel.AppLanguage
import kotlinx.coroutines.launch

// ═══════════════════════════════════════
//  LOTTERY SCREEN
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryScreen(
    language: AppLanguage,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = remember { LotteryRepository() }
    val baseUrl = BuildConfig.API_BASE_URL

    var selectedGame by remember { mutableStateOf("melate") }
    var isLoading by remember { mutableStateOf(value = false) }
    var resultNumbers by remember { mutableStateOf<List<String>>(emptyList()) }
    var extraNumbers by remember { mutableStateOf<List<String>>(emptyList()) }
    var generatedTickets by remember { mutableStateOf<List<LotteryTicket>>(emptyList()) }
    var lastDrawDate by remember { mutableStateOf<String?>(null) }
    var nextDrawDate by remember { mutableStateOf<String?>(null) }
    var nextJackpot by remember { mutableStateOf<String?>(null) }
    var recommendedNumbers by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableIntStateOf(0) } // 0=results, 1=generate

    val games = listOf(
        "melate" to "🇲🇽 Melate",
        "melate_retro" to "🇲🇽 Melate Retro",
        "chispazo" to "🇲🇽 Chispazo",
        "euromillions" to "🇪🇸 EuroMillones",
        "primitiva" to "🇪🇸 La Primitiva",
        "powerball" to "🇺🇸 Powerball",
        "megamillions" to "🇺🇸 Mega Millions",
        "baloto" to "🇨🇴 Baloto",
    )

    fun loadResults() {
        scope.launch {
            isLoading = true; errorMessage = null
            when (val result = repository.getResults(baseUrl, selectedGame)) {
                is ApiResult.Success -> {
                    resultNumbers = result.data.numbers
                    extraNumbers = result.data.extraNumbers
                    lastDrawDate = result.data.drawDate
                }
                is ApiResult.Error -> errorMessage = result.message
            }
            // Also load next draw info
            when (val next = repository.getNextDraw(baseUrl, selectedGame)) {
                is ApiResult.Success -> {
                    nextDrawDate = next.data.date
                    nextJackpot = next.data.jackpot
                }
                is ApiResult.Error -> {} // Silently fail for next draw
            }
            isLoading = false
        }
    }

    fun loadTickets() {
        scope.launch {
            isLoading = true; errorMessage = null
            when (val result = repository.getTickets(baseUrl, selectedGame, 5)) {
                is ApiResult.Success -> generatedTickets = result.data
                is ApiResult.Error -> errorMessage = result.message
            }
            isLoading = false
        }
    }

    fun loadRecommended() {
        scope.launch {
            when (val result = repository.getRecommendedNumbers(baseUrl, selectedGame)) {
                is ApiResult.Success -> recommendedNumbers = result.data
                is ApiResult.Error -> {} // Silently fail
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDarkTheme) Color(0xFF050508) else Color(0xFFF8F9FC)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AdaptiveDimens.spacingSm())) {
                        Box(
                            modifier = Modifier
                                .size(AdaptiveDimens.avatarSmall())
                                .clip(RoundedCornerShape(AdaptiveDimens.cornerSmall()))
                                .background(NexaAccent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) { Text("🎰", fontSize = AdaptiveTypography.bodyMedium()) }
                        Text(NexaStrings.get("lottery", language), fontWeight = FontWeight.Bold, fontSize = AdaptiveTypography.headlineMedium())
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, NexaStrings.get("back", language))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            // Game selector
            val adaptiveInfo = LocalWindowAdaptiveInfo.current
            val contentH = AdaptiveDimens.horizontalPadding()
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = contentH),
                horizontalArrangement = Arrangement.spacedBy(AdaptiveDimens.spacingSm())
            ) {
                items(games) { (key, label) ->
                    val selected = selectedGame == key
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedGame = key
                            resultNumbers = emptyList(); extraNumbers = emptyList()
                            generatedTickets = emptyList(); recommendedNumbers = emptyList()
                            lastDrawDate = null; nextDrawDate = null; nextJackpot = null
                        },
                        label = { Text(label, fontSize = AdaptiveTypography.labelMedium()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexaAccent.copy(alpha = 0.15f),
                            selectedLabelColor = NexaAccent
                        ),
                        border = if (selected) BorderStroke(1.dp, NexaAccent.copy(alpha = 0.3f))
                        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(AdaptiveDimens.spacingSm()))

            // Tabs
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = contentH),
                horizontalArrangement = Arrangement.spacedBy(AdaptiveDimens.spacingSm())) {
                TabButton(text = NexaStrings.get("lottery_results", language), selected = activeTab == 0, onClick = { activeTab = 0 }, modifier = Modifier.weight(1f))
                TabButton(text = NexaStrings.get("lottery_generate", language), selected = activeTab == 1, onClick = { activeTab = 1 }, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(AdaptiveDimens.spacingLg()))

            // Content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = contentH, vertical = AdaptiveDimens.spacingSm()),
                verticalArrangement = Arrangement.spacedBy(AdaptiveDimens.spacingMd())
            ) {
                if (activeTab == 0) {
                    // Results tab
                    item {
                        ActionButton(
                            text = NexaStrings.get("lottery_view_result", language),
                            icon = Icons.Default.Refresh,
                            isLoading = isLoading
                        ) { loadResults() }
                    }

                    if (errorMessage != null) {
                        item { ErrorCard(errorMessage!!) }
                    }

                    if (resultNumbers.isNotEmpty()) {
                        item {
                            ResultCard(
                                numbers = resultNumbers,
                                extraNumbers = extraNumbers,
                                drawDate = lastDrawDate,
                                nextDrawDate = nextDrawDate,
                                jackpot = nextJackpot,
                                language = language
                            )
                        }
                    }

                    // Recommended numbers
                    if (recommendedNumbers.isNotEmpty()) {
                        item {
                            RecommendedCard(numbers = recommendedNumbers, language = language)
                        }
                    }

                    // Load recommended on first view
                    item {
                        LaunchedEffect(selectedGame) { loadRecommended() }
                    }

                } else {
                    // Generate tab
                    item {
                        ActionButton(
                            text = NexaStrings.get("lottery_generate_tickets", language),
                            icon = Icons.Default.Casino,
                            isLoading = isLoading
                        ) { loadTickets() }
                    }

                    if (errorMessage != null) {
                        item { ErrorCard(errorMessage!!) }
                    }

                    items(generatedTickets.size) { index ->
                        TicketCard(ticketNumber = index + 1, ticket = generatedTickets[index], language = language)
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
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = if (selected) NexaAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = if (selected) BorderStroke(1.dp, NexaAccent.copy(alpha = 0.3f))
        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Text(text = text, modifier = Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center,
            fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) NexaAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun ActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isLoading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NexaAccent), enabled = !isLoading) {
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
private fun ResultCard(numbers: List<String>, extraNumbers: List<String>, drawDate: String?, nextDrawDate: String?, jackpot: String?, language: AppLanguage) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            drawDate?.let {
                Text("${NexaStrings.get("lottery_draw", language)}: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }

            // Main numbers
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                numbers.forEach { num -> Ball(number = num) }
            }

            // Extra/bonus numbers
            if (extraNumbers.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    extraNumbers.forEach { num -> Ball(number = num, isBonus = true) }
                }
            }

            // Next draw info
            if ((nextDrawDate != null) || (jackpot != null)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (nextDrawDate != null) {
                        Text("${NexaStrings.get("lottery_next_draw", language)}: $nextDrawDate", fontSize = 12.sp, color = NexaAccent.copy(alpha = 0.6f))
                    }
                    if (jackpot != null) {
                        Text("${NexaStrings.get("lottery_prize", language)}: $jackpot", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NexaAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedCard(numbers: List<String>, language: AppLanguage) {
    Surface(shape = RoundedCornerShape(14.dp), color = NexaAccent.copy(alpha = 0.04f),
        border = BorderStroke(0.5.dp, NexaAccent.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(NexaStrings.get("lottery_recommended", language), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = NexaAccent.copy(alpha = 0.6f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                numbers.forEach { num -> Ball(number = num) }
            }
        }
    }
}

@Composable
private fun Ball(number: String, isBonus: Boolean = false) {
    Box(
        modifier = Modifier.size(42.dp).clip(CircleShape)
            .background(
                Brush.radialGradient(
                    if (isBonus) listOf(Color(0xFFFFD700).copy(alpha = 0.2f), Color(0xFFFFD700).copy(alpha = 0.05f))
                    else listOf(NexaAccent.copy(alpha = 0.2f), NexaAccent.copy(alpha = 0.05f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = number, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = if (isBonus) Color(0xFFFFD700) else NexaAccent)
    }
}

@Composable
private fun TicketCard(ticketNumber: Int, ticket: LotteryTicket, language: AppLanguage) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("${NexaStrings.get("lottery_ticket", language)} #$ticketNumber", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = NexaAccent.copy(alpha = 0.6f))
                Icon(Icons.Default.Casino, null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ticket.numbers.forEach { num -> Ball(number = num) }
            }
            if (ticket.extraNumbers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ticket.extraNumbers.forEach { num -> Ball(number = num, isBonus = true) }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.06f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.1f))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp))
            Text(message, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
}

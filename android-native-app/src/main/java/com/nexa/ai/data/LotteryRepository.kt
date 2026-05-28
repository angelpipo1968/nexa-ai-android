package com.nexa.ai.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class LotteryResult(
    val numbers: List<String> = emptyList(),
    val drawDate: String? = null,
    val drawNumber: String? = null,
    val extraNumbers: List<String> = emptyList()
)

data class LotteryTicket(
    val numbers: List<String> = emptyList(),
    val extraNumbers: List<String> = emptyList()
)

data class LotteryGameInfo(
    val name: String = "",
    val game: String = "",
    val numbersCount: Int = 0,
    val numbersRange: String = "",
    val bonusCount: Int = 0
)

class LotteryRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** Get latest draw results for a game */
    suspend fun getResults(baseUrl: String, game: String): ApiResult<LotteryResult> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/lottery?action=results&game=$game")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext ApiResult.Error("Empty response")

            val json = gson.fromJson(body, JsonObject::class.java)

            if (json.has("error")) {
                return@withContext ApiResult.Error(json.get("error").asString)
            }

            val numbers = mutableListOf<String>()
            val extra = mutableListOf<String>()

            // Parse magayo response format
            if (json.has("numbers")) {
                val nums = json.getAsJsonArray("numbers")
                nums.forEach { numbers.add(it.asString) }
            }
            if (json.has("bonus")) {
                val bonus = json.getAsJsonArray("bonus")
                bonus.forEach { extra.add(it.asString) }
            }
            // Alternative: "result" field as string "01-02-03-04-05-06"
            if (numbers.isEmpty() && json.has("result")) {
                val result = json.get("result").asString
                numbers.addAll(result.split("-", ",", " ").filter { it.isNotBlank() })
            }

            val drawDate = json.get("draw_date")?.asString ?: json.get("date")?.asString
            val drawNumber = json.get("draw_number")?.asString ?: json.get("draw")?.asString

            ApiResult.Success(LotteryResult(
                numbers = numbers,
                drawDate = drawDate,
                drawNumber = drawNumber,
                extraNumbers = extra
            ))
        } catch (e: Exception) {
            ApiResult.Error("Error: ${e.localizedMessage}")
        }
    }

    /** Generate recommended tickets */
    suspend fun getTickets(baseUrl: String, game: String, count: Int = 5): ApiResult<List<LotteryTicket>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/lottery?action=tickets&game=$game&tickets=$count")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext ApiResult.Error("Empty response")

            val json = gson.fromJson(body, JsonObject::class.java)

            if (json.has("error")) {
                return@withContext ApiResult.Error(json.get("error").asString)
            }

            val tickets = mutableListOf<LotteryTicket>()

            if (json.has("tickets")) {
                val ticketsArray = json.getAsJsonArray("tickets")
                ticketsArray.forEach { ticketElement ->
                    val ticketObj = ticketElement.asJsonObject
                    val numbers = mutableListOf<String>()
                    val extra = mutableListOf<String>()

                    if (ticketObj.has("numbers")) {
                        ticketObj.getAsJsonArray("numbers").forEach { numbers.add(it.asString) }
                    }
                    if (ticketObj.has("bonus")) {
                        ticketObj.getAsJsonArray("bonus").forEach { extra.add(it.asString) }
                    }

                    tickets.add(LotteryTicket(numbers = numbers, extraNumbers = extra))
                }
            }

            if (tickets.isEmpty()) {
                // Fallback: generate random numbers locally
                repeat(count) {
                    tickets.add(LotteryTicket(
                        numbers = (1..6).map { (1..50).random().toString().padStart(2, '0') }
                    ))
                }
            }

            ApiResult.Success(tickets)
        } catch (e: Exception) {
            ApiResult.Error("Error: ${e.localizedMessage}")
        }
    }

    /** Get recommended numbers */
    suspend fun getRecommendedNumbers(baseUrl: String, game: String): ApiResult<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/lottery?action=numbers&game=$game")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext ApiResult.Error("Empty response")

            val json = gson.fromJson(body, JsonObject::class.java)

            if (json.has("error")) {
                return@withContext ApiResult.Error(json.get("error").asString)
            }

            val numbers = mutableListOf<String>()
            if (json.has("numbers")) {
                json.getAsJsonArray("numbers").forEach { numbers.add(it.asString) }
            }

            ApiResult.Success(numbers)
        } catch (e: Exception) {
            ApiResult.Error("Error: ${e.localizedMessage}")
        }
    }

    /** Get next draw info */
    suspend fun getNextDraw(baseUrl: String, game: String): ApiResult<NextDrawInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/lottery?action=next_draw&game=$game")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext ApiResult.Error("Empty response")

            val json = gson.fromJson(body, JsonObject::class.java)

            if (json.has("error")) {
                return@withContext ApiResult.Error(json.get("error").asString)
            }

            val date = json.get("next_draw_date")?.asString ?: json.get("date")?.asString ?: "N/A"
            val jackpot = json.get("jackpot")?.asString ?: json.get("prize")?.asString

            ApiResult.Success(NextDrawInfo(date = date, jackpot = jackpot))
        } catch (e: Exception) {
            ApiResult.Error("Error: ${e.localizedMessage}")
        }
    }
}

data class NextDrawInfo(
    val date: String,
    val jackpot: String?
)

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

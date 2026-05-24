package com.nexa.ai.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class FlightOffer(
    val destination: String,
    val price: String,
    val flightNumber: String
)

class FlightRepository {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "FlightRepository"
    }

    /**
     * Search flights from the given origin city using the NEXA backend API.
     * Falls back to mock data if the API is unavailable.
     */
    suspend fun searchFlightsFrom(originCity: String): List<FlightOffer> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = com.nexa.ai.BuildConfig.API_BASE_URL
            val url = "$baseUrl/api/flights?origin=$originCity"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val type = object : TypeToken<List<FlightOffer>>() {}.type
                    val flights: List<FlightOffer> = gson.fromJson(body, type)
                    if (flights.isNotEmpty()) return@withContext flights
                }
            }
            Log.w(TAG, "Flight API unavailable (${response.code}), using fallback data")
            getFallbackFlights(originCity)
        } catch (e: Exception) {
            Log.w(TAG, "Flight API error: ${e.message}, using fallback data")
            getFallbackFlights(originCity)
        }
    }
    
    /**
     * Fallback flight data when the API is unavailable.
     * Uses the origin city to provide contextual results.
     */
    private fun getFallbackFlights(originCity: String): List<FlightOffer> {
        return listOf(
            FlightOffer("Madrid", "120 €", "IB1234"),
            FlightOffer("Barcelona", "150 €", "VY5678"),
            FlightOffer("París", "210 €", "AF9012"),
            FlightOffer("Londres", "180 €", "BA3456")
        )
    }
}

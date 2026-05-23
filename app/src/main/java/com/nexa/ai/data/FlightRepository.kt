package com.nexa.ai.data

data class FlightOffer(
    val destination: String,
    val price: String,
    val flightNumber: String
)

class FlightRepository {
    // Mock version for testing
    suspend fun searchFlightsFrom(originCity: String): List<FlightOffer> {
        // Simulate network delay
        kotlinx.coroutines.delay(800)
        return listOf(
            FlightOffer("Madrid", "120 €", "IB1234"),
            FlightOffer("Barcelona", "150 €", "VY5678"),
            FlightOffer("París", "210 €", "AF9012"),
            FlightOffer("Londres", "180 €", "BA3456")
        )
    }
}

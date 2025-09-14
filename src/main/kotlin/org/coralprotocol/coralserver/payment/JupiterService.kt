package org.coralprotocol.coralserver.payment

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.config.CORAL_MAINNET_MINT
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.server.apiJsonConfig

@Serializable
private class Price(
    @Suppress("unused")
    val blockId: Long? = null,

    @Suppress("unused")
    val decimals: Long,
    val usdPrice: Double,

    @Suppress("unused")
    val priceChange24h: Double? = null
)

class JupiterService {
    private var lastUpdate: Long? = null
    private var lastPrice: Price? = null

    private val httpClass = HttpClient(CIO) {
        install(ContentNegotiation)
    }

    private suspend fun fetchPrice(): Price {
        val response = httpClass.get("https://lite-api.jup.ag/price/v3") {
            url {
                parameters.append("ids", CORAL_MAINNET_MINT)
            }
        }

        if (response.status.value != 200) {
            throw RouteException(response.status, "Unexpected response from Jupiter API")
        }

        val price = apiJsonConfig.decodeFromString<Map<String, Price>>(response.bodyAsText())[CORAL_MAINNET_MINT]
            ?: throw RouteException(HttpStatusCode.BadRequest, "Jupiter did not provide a conversion for $CORAL_MAINNET_MINT")

        lastPrice = price
        lastUpdate = System.currentTimeMillis()

        return price
    }

    private suspend fun getPrice(): Price {
        val lastPrice = this.lastPrice
        val lastUpdate = this.lastUpdate
        return if (lastPrice == null) {
            fetchPrice()
        }
        else {
            if (lastUpdate == null || lastUpdate + 6000 < System.currentTimeMillis()) {
                fetchPrice()
            }
            else {
                lastPrice
            }
        }
    }

    /**
     * Converts a given Coral token count to USD cents.
     *
     * Important notes:
     * - This function gives an ESTIMATE. Accuracy is not guaranteed and should not be relied upon.
     * - This function relies on a tier beta Jupiter API: https://dev.jup.ag/docs/api/price-api/v3/price
     * - Because the API is free, there is a rate limit applied.  A cached version of the API response will be used if
     *   usage exceeds the limit.
     * - The real limit is 60 requests per minute, to avoid possible issues from e.g., more than one Coral server running
     *   on the same machine, this limit is lowered to 10 requests per minute, one request every 6 seconds.
     *
     *  @param coralAmount The Coral amount to convert.
     */
    suspend fun coralToUsd(coralAmount: Double): Double {
        return coralAmount * getPrice().usdPrice
    }

    /**
     * Converts a given Coral token count to USD cents.
     * Important notes: @see coralToUsdCents
     */
    suspend fun usdToCoral(usdAmount: Double): Double {
        return usdAmount / getPrice().usdPrice
    }
}
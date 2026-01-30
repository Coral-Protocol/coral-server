package org.coralprotocol.coralserver.agent.registry


import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_CONFIG
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.time.measureTimedValue

class MarketplaceAgentRegistrySource(marketplaceCatalog: List<RegistryAgentCatalog>) :
    AgentRegistrySource(AgentRegistrySourceIdentifier.Marketplace) {

    companion object {
        private const val BASE_URL = "https://marketplace.coralprotocol.ai/api/v1"

        suspend fun initialiseMarketplaceAgentRegistrySource(
            logger: Logger,
            client: HttpClient
        ): MarketplaceAgentRegistrySource {
            val url = URLBuilder(urlString = BASE_URL).appendPathSegments("agents").build()
            logger.info { "fetching marketplace agents" }

            val timedAgentResponse = measureTimedValue {
                client.get(url).body<List<RegistryAgentCatalog>>()
            }

            logger.info { "fetched ${timedAgentResponse.value.size} agents from the marketplace in ${timedAgentResponse.duration}" }
            return MarketplaceAgentRegistrySource(timedAgentResponse.value)
        }
    }

    val client by inject<HttpClient>()
    val logger by inject<Logger>(named(LOGGER_CONFIG))

    override val agents: MutableList<RegistryAgentCatalog> = marketplaceCatalog.toMutableList()

    override suspend fun resolveAgent(agent: RegistryAgentIdentifier): RestrictedRegistryAgent {
        logger.debug { "resolving marketplace agent $agent" }

        val url = URLBuilder(urlString = BASE_URL).appendPathSegments("agents", agent.name, agent.version).build()
        val timedAgentResponse = measureTimedValue {
            client.get(url).body<RestrictedRegistryAgent>()
        }

        logger.debug { "resolved marketplace agent $agent in ${timedAgentResponse.duration}" }

        return timedAgentResponse.value
    }
}
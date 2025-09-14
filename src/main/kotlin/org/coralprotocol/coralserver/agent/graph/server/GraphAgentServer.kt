@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph.server

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.agent.graph.PaidGraphAgentRequest
import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier
import org.coralprotocol.coralserver.agent.registry.PublicAgentExportSettingsMap
import org.coralprotocol.coralserver.routes.api.v1.Agents
import org.coralprotocol.coralserver.routes.api.v1.PublicWallet
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.server.apiJsonConfig

/**
 * This class represents another Coral server, or any server capable of providing remote agents... which is for right
 * now only another Coral server.
 */
@Serializable
class GraphAgentServer (
    val address: String,
    val port: UShort,
    val secure: Boolean, // true = https, false = http
    val attributes: List<GraphAgentServerAttribute>
)  {
    @Transient
    private val client = HttpClient(CIO) {
        install(Resources)
        install(ContentNegotiation) {
            json(apiJsonConfig)
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
            host = this@GraphAgentServer.address
            port = this@GraphAgentServer.port.toInt()
            url {
                protocol = if (secure) URLProtocol.HTTPS else URLProtocol.HTTP
            }
        }
    }

    /**
     * Gets the public wallet address for this server.
     * @throws RouteException if the request fails.
     */
    suspend fun getWallet(): String {
        val resource = PublicWallet()
        val response = client.get(resource)

        val body = response.bodyAsText()
        if (response.status == HttpStatusCode.OK) {
            return body
        }
        else {
            throw apiJsonConfig.decodeFromString<RouteException>(body)
        }
    }

    /**
     * Gets the export map for a specified agent in this server
     * @throws RouteException if the request fails.
     * @see Agents.ExportedAgent
     */
    suspend fun getAgentExportSettings(id: AgentRegistryIdentifier): PublicAgentExportSettingsMap {
        val resource = Agents.ExportedAgent(
            name = id.name,
            version = id.version
        )
        val response = client.get(resource)
        println("Getting export settings from $this for agent $id")

        val body = response.bodyAsText()
        if (response.status == HttpStatusCode.OK) {
            return apiJsonConfig.decodeFromString<PublicAgentExportSettingsMap>(body)
        }
        else {
            throw apiJsonConfig.decodeFromString<RouteException>(body)
        }
    }

    override fun toString(): String {
        return "${if (secure) "https://" else "http://"}$address:$port"
    }

    /**
     * Creates a claim for an agent, returning the claim ID
     * @throws RouteException if the request fails.
     * @see Agents.ExportedAgent
     */
    suspend fun createClaim(paidGraphAgentRequest: PaidGraphAgentRequest): String {
        val response = client.post(Agents.Claim()) {
            contentType(ContentType.Application.Json)
            setBody(paidGraphAgentRequest)
        }

        val body = response.bodyAsText()
        if (response.status == HttpStatusCode.OK) {
            return body // claim ID
        }
        else {
            throw apiJsonConfig.decodeFromString<RouteException>(body)
        }
    }
}
@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph.server

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * This class represents another Coral server, or any server capable of providing remote agents... which is for right
 * now only another Coral server.
 */
@Serializable
class GraphAgentServer(
    val address: String,
    val port: UShort,
    val secure: Boolean, // true = https, false = http
    val attributes: List<GraphAgentServerAttribute>
) : KoinComponent {
    private val json by inject<Json>()

    @Transient
    private val client = HttpClient(CIO) {
        install(Resources)
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
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

    override fun toString(): String {
        return "${if (secure) "https://" else "http://"}$address:$port"
    }
}
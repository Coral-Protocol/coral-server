@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent

@Serializable
class GraphAgentServer(
    val address: String,
    val port: UShort,
    val secure: Boolean, // true = https, false = http
    val attributes: List<GraphAgentServerAttribute>
) : KoinComponent {
    override fun toString(): String {
        @Suppress("HttpUrlsUsage")
        return "${if (secure) "https://" else "http://"}$address:$port"
    }
}
package org.coralprotocol.coralserver.server

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import org.coralprotocol.coralserver.tools.addThreadTools

/**
 * Configures and returns a new server instance with default settings.
 */
fun createCoralMcpServer(): Server {
    val server = Server(
        Implementation(
            name = "Coral Server",
            version = "0.1.0"
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                tools = ServerCapabilities.Tools(listChanged = true),
            )
        ),
    )

    // Add thread-based tools
    server.addThreadTools()

    return server
}
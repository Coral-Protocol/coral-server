package org.coralprotocol.coralserver.util

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.session.LocalSession

fun HttpClient.mcpFunctionRuntime(
    name: String,
    version: String,
    func: suspend (Client, LocalSession) -> Unit
) =
    FunctionRuntime { executionContext, applicationRuntimeContext ->
        val mcpClient = Client(
            clientInfo = Implementation(
                name = name,
                version = version
            )
        )

        val transport = SseClientTransport(
            client = this,
            urlString = applicationRuntimeContext.getMcpUrl(
                executionContext,
                AddressConsumer.LOCAL
            ).toString()
        )

        mcpClient.connect(transport)
        func(mcpClient, executionContext.session)
    }
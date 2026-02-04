package org.coralprotocol.coralserver.util

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.session.LocalSession

fun HttpClient.sseFunctionRuntime(
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
            urlString = applicationRuntimeContext.getSseUrl(
                executionContext,
                AddressConsumer.LOCAL
            ).toString()
        )

        mcpClient.connect(transport)
        func(mcpClient, executionContext.session)
    }

fun HttpClient.streamableHttpFunctionRuntime(
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

        val transport = StreamableHttpClientTransport(
            client = this,
            url = applicationRuntimeContext.getStreamableHttpUrl(
                executionContext,
                AddressConsumer.LOCAL
            ).toString()
        )

        mcpClient.connect(transport)
        func(mcpClient, executionContext.session)
    }
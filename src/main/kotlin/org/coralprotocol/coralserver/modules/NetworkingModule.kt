package org.coralprotocol.coralserver.modules

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.sse.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.modules.ktor.coralServerModule
import org.koin.dsl.module

val networkModule = module {
    single {
        val config = get<NetworkConfig>()
        HttpClient {
            install(Resources)
            install(SSE)
            install(ContentNegotiation) {
                json(get(), contentType = ContentType.Application.Json)
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
                host = config.bindAddress
                port = config.bindPort.toInt()
            }
        }
    }

    single {
        val config = get<NetworkConfig>()
        embeddedServer(
            CIO,
            host = config.bindAddress,
            port = config.bindPort.toInt(),
            watchPaths = listOf("classes")
        ) {
            coralServerModule()
        }
    }
}
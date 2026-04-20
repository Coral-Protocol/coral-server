@file:OptIn(ExperimentalHoplite::class)

package org.coralprotocol.coralserver

import com.sksamuel.hoplite.ExperimentalHoplite
import dev.eav.tomlkt.Toml
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.modules.*
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.environmentProperties
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket

fun main(args: Array<String>) {
    val app = startKoin {
        environmentProperties()
        modules(
            configModule,
            configModuleParts,
            loggingModule,
            namedLoggingModule,
            blockchainModule,
            networkModule,
            agentModule,
            sessionModule,
            module {
                single {
                    Json {
                        encodeDefaults = true
                        prettyPrint = true
                        explicitNulls = false
                    }
                }
                single {
                    Toml {
                        // currently only used for loading coral-agent.toml files, to allow as many newer coral-agent.toml files
                        // as possible on earlier versions of the server, this must be set to true
                        ignoreUnknownKeys = true
                    }
                }
            }
        )
        createEagerInstances()
    }

    val networkConfig: NetworkConfig = app.koin.get()
    ensureServerPortAvailable(networkConfig.bindAddress, networkConfig.bindPort.toInt())

    val server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> = app.koin.get()
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop()
    })

    server.start(wait = true)
}

internal fun ensureServerPortAvailable(bindAddress: String, bindPort: Int) {
    if (bindPort == 0) return

    try {
        ServerSocket().use { socket ->
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(bindAddress, bindPort))
        }
    } catch (exception: BindException) {
        throw IllegalStateException(
            "Cannot start Coral server because $bindAddress:$bindPort is already in use. " +
                "Stop the existing process or configure [network].bindPort to a free port.",
            exception
        )
    }
}

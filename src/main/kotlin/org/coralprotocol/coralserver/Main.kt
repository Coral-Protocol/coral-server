@file:OptIn(ExperimentalHoplite::class)

package org.coralprotocol.coralserver

import com.sksamuel.hoplite.ExperimentalHoplite
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import org.coralprotocol.coralserver.modules.*
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.environmentProperties

fun main(args: Array<String>) {
    val app = startKoin {
        environmentProperties()
        modules(
            configModule,
            configModuleParts,
            loggingModule,
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
                        ignoreUnknownKeys = false
                    }
                }
            }
        )
        createEagerInstances()
    }

    val server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> = app.koin.get()
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop()
    })

    server.start(wait = true)
}

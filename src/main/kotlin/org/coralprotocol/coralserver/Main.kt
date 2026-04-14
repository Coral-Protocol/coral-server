@file:OptIn(ExperimentalHoplite::class)

package org.coralprotocol.coralserver

import com.sksamuel.hoplite.ExperimentalHoplite
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.serialization.json.Json
import dev.eav.tomlkt.Toml
import org.coralprotocol.coralserver.config.CommandLineArgs
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.modules.*
import org.coralprotocol.coralserver.util.isWindows
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.environmentProperties
import java.net.BindException
import kotlin.reflect.KClass

fun main(args: Array<String>) {
    val app = startKoin {
        environmentProperties()
        modules(
            module { single { CommandLineArgs(args) } },
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


    try {
        val server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> = app.koin.get()
        Runtime.getRuntime().addShutdownHook(Thread {
            server.stop()
        })
        server.start(wait = true)
    } catch (e: Exception) {
        if (e.hasCause(BindException::class)) {
            // Those familiar with cause-first style Exceptions will see stacktrace first and don't need guidance.
            e.printStackTrace()
            // Many python developers are unfamiliar with cause-first style exceptions and will miss the stacktrace.
            // This crowd also is unlikely to know what "BindException: Address already in use" means.
            printBindExceptionGuidance(app.koin.get<NetworkConfig>().bindPort)
        } else {
            throw e
        }
    }
}

private fun printBindExceptionGuidance(port: UShort) {
    println("\n\n${"=".repeat(60)}")
    println("Probably port $port is already in use.")
    println("Likely another Coral instance is already running.")
    println("Please close the other instance and try again.")
    println()
    if (isWindows()) {
        println("With PowerShell, you can kill whatever is using the port with:")
        println("Get-Process -Id (Get-NetTCPConnection -LocalPort $port).OwningProcess | Stop-Process -Force")
    } else {
        println("With bash, you can kill whatever is using the port with:")
        println()
        println("lsof -t -i :$port | xargs kill -9")
        println()
    }
    println("${"=".repeat(60)}\n\n")
}

private fun Throwable.hasCause(type: KClass<out Throwable>) =
    generateSequence(this) { it.cause }.any { type.isInstance(it) }


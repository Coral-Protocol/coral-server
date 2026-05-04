package org.coralprotocol.coralserver.dsl

import dev.eav.tomlkt.Toml
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.config.*
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.*
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools
import org.slf4j.LoggerFactory

/**
 * Main entry point for the Coral library.
 * This object provides methods to initialize the Coral environment and create sessions.
 */
object Coral {

    fun createDefaultConfig(
        authToken: String = "default-token",
        openAIProxy: CoralLlmProxy? = null,
        anthropicProxy: CoralLlmProxy? = null
    ): RootConfig {
        val providers = mutableListOf<LlmProxyProviderConfig>()
        openAIProxy?.let { providers.add(it.providerConfig) }
        anthropicProxy?.let { providers.add(it.providerConfig) }

        return RootConfig(
            networkConfig = NetworkConfig(bindPort = 0u),
            authConfig = AuthConfig(keys = setOf(authToken)),
            loggingConfig = LoggingConfig(consoleLogLevel = org.slf4j.event.Level.INFO),
            llmProxyConfig = LlmProxyConfig(
                providers = providers
            )
        )
    }

    fun init(
        config: RootConfig = createDefaultConfig(),
        managementScope: CoroutineScope = GlobalScope
    ) {
        if (GlobalContext.getOrNull() != null) return

        val logger = Logger(1024, LoggerFactory.getLogger("Coral"))
        startKoin {
            modules(
                module {
                    single { config }
                    singleOf(::ApplicationRuntimeContext)
                    single {
                        Json {
                            encodeDefaults = true
                            prettyPrint = true
                            explicitNulls = false
                        }
                    }
                    single {
                        Toml {
                            ignoreUnknownKeys = true
                        }
                    }
                    single {
                        HttpClient {
                            install(Resources)
                            install(WebSockets)
                            install(SSE)
                            install(ContentNegotiation) {
                                json(get(), contentType = ContentType.Application.Json)
                            }
                        }
                    }
                },
                configModuleParts,
                loggingModule,
                llmProxyModule(false),
                blockchainModule,
                agentModule,
                module {
                    single {
                        LocalSessionManager(
                            blockchainService = get(),
                            jupiterService = get(),
                            httpClient = get(),
                            config = get(),
                            json = get(),
                            managementScope = managementScope,
                            supervisedSessions = false,
                            logger = logger
                        )
                    }
                    single(named(LOGGER_LOCAL_SESSION)) { logger }
                    single(named(LOGGER_ROUTES)) { logger }
                    single(named(LOGGER_CONFIG)) { logger }
                    single(named(LOGGER_LLM_PROXY)) { logger }
                }
            )
        }
    }

    suspend fun createSession(name: String, graph: AgentGraph): LocalSession {
        val koin = KoinPlatformTools.defaultContext().get()
        val manager = koin.get<LocalSessionManager>()
        return manager.createSession(name, graph).first
    }

    fun shutdown() {
        stopKoin()
    }
}

suspend fun coralSession(name: String, graph: AgentGraph): LocalSession = Coral.createSession(name, graph)

suspend fun coralSession(name: String, block: AgentGraphBuilder.() -> Unit): LocalSession =
    Coral.createSession(name, agentGraph(block))

fun coralInit(config: RootConfig = Coral.createDefaultConfig()) = Coral.init(config)

fun coralShutdown() = Coral.shutdown()

package org.coralprotocol.coralserver

import io.kotest.core.spec.RootTest
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.plus
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.config.*
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.WEBSOCKET_COROUTINE_SCOPE_NAME
import org.coralprotocol.coralserver.modules.agentModule
import org.coralprotocol.coralserver.modules.blockchainModule
import org.coralprotocol.coralserver.modules.configModuleParts
import org.coralprotocol.coralserver.modules.ktor.coralServerModule
import org.coralprotocol.coralserver.modules.loggingModule
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.logger.PrintLogger
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.environmentProperties
import org.koin.test.KoinTest
import java.util.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

@Suppress("UNCHECKED_CAST")
abstract class CoralTest(body: CoralTest.() -> Unit) : KoinTest, FunSpec(body as FunSpec.() -> Unit) {
    val authToken = UUID.randomUUID().toString()
    val unitTestSecret = UUID.randomUUID().toString()
    val logBufferSize = 1024
    val config = RootConfig(
        // port for testing is zero
        networkConfig = NetworkConfig(
            bindPort = 0u
        ),
        paymentConfig = PaymentConfig(
            wallets = listOf(
                Wallet.Solana(
                    name = "fake test wallet",
                    cluster = SolanaCluster.DEV_NET,
                    keypairPath = "fake-test-wallet.json",
                    walletAddress = "this is not a real wallet address"
                )
            ),
            remoteAgentWalletName = "fake test wallet"
        ),
        registryConfig = RegistryConfig(
            includeDebugAgents = true
        ),
        authConfig = AuthConfig(
            keys = setOf(authToken)
        ),
        debugConfig = DebugConfig(
            additionalDockerEnvironment = mapOf("UNIT_TEST_SECRET" to unitTestSecret),
            additionalExecutableEnvironment = mapOf("UNIT_TEST_SECRET" to unitTestSecret)
        ),
        loggingConfig = LoggingConfig(
            logBufferSize = logBufferSize.toUInt(),
        )
    )

    fun HttpRequestBuilder.withAuthToken() {
        headers.append(HttpHeaders.Authorization, "Bearer $authToken")
    }

    suspend inline fun <reified T : Any> HttpClient.authenticatedPost(
        resource: T,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): HttpResponse {
        return post(resource) {
            withAuthToken()
            contentType(ContentType.Application.Json)
            builder()
        }
    }

    suspend inline fun <reified T : Any> HttpClient.authenticatedGet(
        resource: T,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): HttpResponse {
        return get(resource) {
            withAuthToken()
            contentType(ContentType.Application.Json)
            builder()
        }
    }

    suspend inline fun <reified T : Any> HttpClient.authenticatedDelete(
        resource: T,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): HttpResponse {
        return delete(resource) {
            withAuthToken()
            contentType(ContentType.Application.Json)
            builder()
        }
    }

    override fun add(test: RootTest) {
        super.add(
            RootTest(
                name = test.name,
                test = {
                    startKoin {
                        environmentProperties()
                        logger(PrintLogger())
                        modules(
                            loggingModule,
                            configModuleParts,
                            blockchainModule,
                            agentModule,
                            module {
                                singleOf(::ApplicationRuntimeContext)
                                single { config }
                                single {
                                    Json {
                                        encodeDefaults = true
                                        prettyPrint = true
                                        explicitNulls = false
                                    }
                                }
                            },
                            module {
                                single {
                                    LocalSessionManager(
                                        blockchainService = get(),
                                        jupiterService = get(),
                                        httpClient = get(),
                                        config = get(),
                                        json = get(),
                                        managementScope = this@RootTest,

                                        // if this is true, exceptions thrown (including assertions) in an agent's runtime will not exit a test
                                        // it also requires that session's coroutine scopes are canceled
                                        supervisedSessions = false,

                                        logger = get()
                                    )
                                }
                                single(named(WEBSOCKET_COROUTINE_SCOPE_NAME)) {
                                    this@RootTest + Job()
                                }
                            }
                        )
                        createEagerInstances()
                    }

                    try {

                        runTestApplication {
                            loadKoinModules(module {
                                single<HttpClient> {
                                    createClient {
                                        install(Resources)
                                        install(WebSockets)
                                        install(SSE)
                                        install(HttpCookies)
                                        install(ClientContentNegotiation) {
                                            json(get(), contentType = ContentType.Application.Json)
                                        }
                                    }
                                }
                            })

                            application {
                                coralServerModule()
                            }

                            loadKoinModules(module { single { application } })

                            test.test(this@RootTest)
                        }
                    } finally {
                        stopKoin()
                    }
                },
                type = test.type,
                source = test.source,
                disabled = test.disabled,
                config = test.config,
                factoryId = test.factoryId
            )
        )
    }
}
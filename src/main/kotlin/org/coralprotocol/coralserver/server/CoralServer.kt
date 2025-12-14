@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.config.OutputFormat
import io.github.smiley4.ktoropenapi.config.SchemaGenerator
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.schemakenerator.core.CoreSteps.addMissingSupertypeSubtypeRelations
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.addJsonClassDiscriminatorProperty
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.analyzeTypeUsingKotlinxSerialization
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.compileReferencingRoot
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.customizeTypes
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.generateSwaggerSchema
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.handleCoreAnnotations
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.handleSchemaAnnotations
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.withTitle
import io.github.smiley4.schemakenerator.swagger.TitleBuilder
import io.github.smiley4.schemakenerator.swagger.data.TitleType
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.uri
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import javassist.CtNewMethod.wrapped
import kotlinx.coroutines.Job
import kotlinx.html.Entities
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.payment.exporting.AggregatedPaymentClaimManager
import org.coralprotocol.coralserver.routes.api.v1.agentRentalApi
import org.coralprotocol.coralserver.routes.api.v1.agentRpcApi
import org.coralprotocol.coralserver.routes.api.v1.registryApi
import org.coralprotocol.coralserver.routes.api.v1.sessionApi
import org.coralprotocol.coralserver.routes.sse.v1.mcpRoutes
import org.coralprotocol.coralserver.routes.ui.consoleUi
import org.coralprotocol.coralserver.routes.ui.documentationInterface
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionException
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.X402Service
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

val apiJsonConfig = Json {
    encodeDefaults = true
    prettyPrint = true
    explicitNulls = false
}

/**
 * CoralServer class that encapsulates the SSE MCP server functionality.
 *
 * @param host The host to run the server on
 * @param port The port to run the server on
 * @param devmode Whether the server is running in development mode
 */
class CoralServer(
    val config: Config,
    val registry: AgentRegistry,
    val blockchainService: BlockchainService? = null,
    val x402Service: X402Service? = null,
    val launchMode: LaunchMode = LaunchMode.DEDICATED
) {
    val jupiterService = JupiterService()
    val localSessionManager = LocalSessionManager(blockchainService, ApplicationRuntimeContext(config), jupiterService)

    val aggregatedPaymentClaimManager = if (blockchainService != null) {
        AggregatedPaymentClaimManager(blockchainService, jupiterService)
    } else {
        null
    }

//    val remoteSessionManager = if (aggregatedPaymentClaimManager != null) {
//        RemoteSessionManager(null, aggregatedPaymentClaimManager)
//    }
//    else {
//        null
//    }

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
        embeddedServer(
            CIO,
            host = config.networkConfig.bindAddress,
            port = config.networkConfig.bindPort.toInt(),
            watchPaths = listOf("classes")
        ) {
            install(OpenApi) {
                info {
                    title = "Coral Server API"
                    version = "1.0"
                }
                tags {
                    tagGenerator = { url -> listOf(url.getOrNull(2)) }
                }
                security {
                    securityScheme("token") {
                        type = AuthType.HTTP
                        scheme = AuthScheme.BEARER
                        bearerFormat = "Configured token"
                    }
                    securityScheme("agentSecret") {
                        type = AuthType.HTTP
                        scheme = AuthScheme.BEARER
                        bearerFormat = "Generated agent secret"
                    }
                }
                schemas {
                    generator = SchemaGenerator.kotlinx { }
                    // Generated types from routes
                    generator = { type ->
                        type
                            .analyzeTypeUsingKotlinxSerialization {

                            }
                            .addMissingSupertypeSubtypeRelations()
                            .addJsonClassDiscriminatorProperty()
                            .generateSwaggerSchema({
                                strictDiscriminatorProperty = true
                            })
                            .handleCoreAnnotations()
                            .handleSchemaAnnotations()
                            .customizeTypes { _, schema ->
                                // Mapping is broken, and one of the code generation libraries I am using checks the
                                // references here
                                schema.discriminator?.mapping = null;
                            }
                            .withTitle(TitleType.SIMPLE)
                            .compileReferencingRoot(
                                explicitNullTypes = false,
                                inlineDiscriminatedTypes = true,
                                builder = TitleBuilder.BUILDER_OPENAPI_SIMPLE
                            )
                    }

                    // Mcp types
                    schema<McpToolName>("McpToolName")
                }
                specAssigner = { url: String, tags: List<String> ->
                    // when another spec version is added, determine the version based on the url here or use
                    // specVersion on the new routes
                    "v1"
                }
                pathFilter = { method, parts ->
                    parts.getOrNull(0) == "api"
                }
                outputFormat = OutputFormat.JSON
            }
            install(Resources)
            install(SSE)
            install(ContentNegotiation) {
                json(apiJsonConfig, contentType = ContentType.Application.Json)
            }
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
                pingPeriod = 5.seconds
                timeout = 15.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            // TODO: probably restrict this down the line
            install(CORS) {
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
                allowHeader(HttpHeaders.AccessControlAllowOrigin)
                allowHeader(HttpHeaders.ContentType)
                allowCredentials = true

                if (config.networkConfig.allowAnyHost)
                    anyHost()
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    // Other exceptions should still be serialized, wrap non RouteException type exceptions in a
                    // RouteException, giving a 500-status code
                    var routeException = if (cause !is RouteException) {
                        logger.error(cause) { "Unexpected exception thrown from route ${call.request.uri}" }
                        RouteException(HttpStatusCode.InternalServerError, cause)
                    }
                    else {
                        cause
                    }

                    call.respond(routeException.status, routeException)
                }
            }
            install(Authentication) {
                bearer("token") {
                    authenticate { credential ->
                        if (!config.auth.keys.contains(credential.token))
                            return@authenticate null
                    }
                }

                bearer("agentSecret") {
                    authenticate { credential ->
                        try {
                            val agentLocator = localSessionManager.locateAgent(credential.token)
                            return@authenticate agentLocator.agent
                        } catch (_: SessionException.InvalidAgentSecret) {
                            return@authenticate null
                        }
                    }
                }
            }
            routing {
                route("api") {
                    route("v1") {
                        authenticate("token") {
                            sessionApi(registry, localSessionManager)
                            registryApi(registry)
                        }

                        authenticate("agentSecret") {
                            agentRpcApi(localSessionManager, x402Service)
                        }

                        agentRentalApi(
                            config.paymentConfig.remoteAgentWallet,
                            registry,
                            blockchainService,
                            null
                        )
                    }
                }

                route("sse") {
                    route("v1") {
                        mcpRoutes(localSessionManager)
                    }
                }

                route("ws") {
                    route("v1") {
//                        debugWsRoutes(localSessionManager, orchestrator)
//                        exportedAgentRoutes(remoteSessionManager)
                    }
                }

                // source of truth for OpenAPI docs/codegen
                route("api_v1.json") {
                    openApi("v1")
                }

                route("ui") {
                    documentationInterface()
                    consoleUi()
                }
            }.run {
                getAllRoutes().forEach {
                    logger.info {
                        "${it.selector} ${it.parent}"
                    }
                }
            }
        }

    val monitor get() = server.monitor
    private var serverJob: Job? = null

    /**
     * Starts the server.
     */
    fun start(wait: Boolean = false) {
        logger.info { "Starting sse server on port ${config.networkConfig.bindPort}" }
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");

        server.start(wait)
        server.application.routing { }.getAllRoutes()
            .forEach { logger.info { it.toString() } }
    }

    /**
     * Stops the server.
     */
    fun stop() {
        logger.info { "Stopping server..." }
        serverJob?.cancel()
        server.stop(1000, 2000)
        serverJob = null
        logger.info { "Server stopped" }
    }
}

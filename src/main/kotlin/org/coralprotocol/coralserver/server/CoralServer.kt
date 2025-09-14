@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.OpenApi
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
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.Job
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.mcp.McpResources
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.mcp.tools.models.McpToolResult
import org.coralprotocol.coralserver.models.SocketEvent
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.payment.exporting.AggregatedPaymentClaimManager
import org.coralprotocol.coralserver.routes.api.v1.*
import org.coralprotocol.coralserver.routes.sse.v1.connectionSseRoutes
import org.coralprotocol.coralserver.routes.sse.v1.exportedAgentSseRoutes
import org.coralprotocol.coralserver.routes.ws.v1.debugWsRoutes
import org.coralprotocol.coralserver.routes.ws.v1.exportedAgentRoutes
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.remote.RemoteSessionManager
import org.coralprotocol.payment.blockchain.BlockchainService
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
    val blockchainService: BlockchainService?,
    val devmode: Boolean = false,
    orchestrator: Orchestrator
) {
    val jupiterService = JupiterService()
    val localSessionManager = LocalSessionManager(config, orchestrator, blockchainService, jupiterService)

    val aggregatedPaymentClaimManager = if (blockchainService != null) {
        AggregatedPaymentClaimManager(blockchainService, jupiterService)
    }
    else {
        null
    }

    val remoteSessionManager = if (aggregatedPaymentClaimManager != null) {
        RemoteSessionManager(orchestrator, aggregatedPaymentClaimManager)
    }
    else {
        null
    }

    private val mcpServersByTransportId = ConcurrentMap<String, Server>()
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
                schemas {
                    generator = SchemaGenerator.kotlinx {  }
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

                    // Other types, used by SSE or WebSocket routes
                    schema<SocketEvent>("SocketEvent")

                    // Mcp types
                    schema<McpToolName>("McpToolName")
                    schema<McpResources>("McpResources")
                    schema<McpToolResult>("McpToolResult")
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
                anyHost()
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    // Other exceptions should still be serialized, wrap non RouteException type exceptions in a
                    // RouteException, giving a 500-status code
                    var wrapped = cause
                    if (cause !is RouteException) {
                        wrapped = RouteException(HttpStatusCode.InternalServerError, cause.message)
                    }

                    call.respondText(text = apiJsonConfig.encodeToString(wrapped), status = wrapped.status)
                }
            }
            routing {
                // api
                debugApiRoutes(localSessionManager)
                sessionApiRoutes(registry, localSessionManager, devmode)
                messageApiRoutes(mcpServersByTransportId, localSessionManager, remoteSessionManager)
                telemetryApiRoutes(localSessionManager)
                documentationApiRoutes()
                agentApiRoutes(registry, blockchainService, remoteSessionManager, jupiterService)
                internalRoutes(remoteSessionManager, aggregatedPaymentClaimManager, jupiterService)
                publicWalletApiRoutes(config.paymentConfig.wallet)

                // sse
                connectionSseRoutes(mcpServersByTransportId, localSessionManager)
                exportedAgentSseRoutes(mcpServersByTransportId, remoteSessionManager)

                // websocket
                debugWsRoutes(localSessionManager, orchestrator)
                exportedAgentRoutes(remoteSessionManager)

                // source of truth for OpenAPI docs/codegen
                route("api_v1.json") {
                    openApi("v1")
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

        if (devmode) {
            logger.info {
                "In development, agents can connect to " +
                        "${config.resolveAddress(AddressConsumer.LOCAL)}/sse/v1/exampleApplicationId/examplePrivacyKey/exampleSessionId/sse?agentId=exampleAgent"
            }
            logger.info {
                "Connect the inspector to " +
                        "${config.resolveAddress(AddressConsumer.LOCAL)}/sse/v1/devmode/exampleApplicationId/examplePrivacyKey/exampleSessionId/sse?agentId=inspector"
            }
        }
        server.start(wait)
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

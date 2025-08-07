@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.OutputFormat
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.schemakenerator.core.CoreSteps.addMissingSupertypeSubtypeRelations
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.addJsonClassDiscriminatorProperty
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.analyzeTypeUsingKotlinxSerialization
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.compileInlining
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.customizeTypes
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.generateSwaggerSchema
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
import kotlinx.serialization.json.JsonNamingStrategy
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.debug.debugRoutes
import org.coralprotocol.coralserver.models.serialization.compileHybridInline
import org.coralprotocol.coralserver.routes.api.v1.agentRoutes
import org.coralprotocol.coralserver.routes.api.v1.documentationRoutes
import org.coralprotocol.coralserver.routes.api.v1.messageRoutes
import org.coralprotocol.coralserver.routes.api.v1.sessionRoutes
import org.coralprotocol.coralserver.routes.api.v1.telemetryRoutes
import org.coralprotocol.coralserver.routes.sse.v1.sseRoutes
import org.coralprotocol.coralserver.session.SessionManager
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

private val json = Json {
    encodeDefaults = true
    prettyPrint = true
    explicitNulls = false
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/**
 * CoralServer class that encapsulates the SSE MCP server functionality.
 *
 * @param host The host to run the server on
 * @param port The port to run the server on
 * @param devmode Whether the server is running in development mode
 */
class CoralServer(
    val host: String = "0.0.0.0",
    val port: UShort = 5555u,
    val appConfig: AppConfigLoader,
    val devmode: Boolean = false,
    val sessionManager: SessionManager = SessionManager(port = port),
) {

    private val mcpServersByTransportId = ConcurrentMap<String, Server>()
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
        embeddedServer(CIO, host = host, port = port.toInt(), watchPaths = listOf("classes")) {
            install(OpenApi) {
                info {
                    title = "Coral Server API"
                }
                spec("v1") {
                    info {
                        version = "1.0"
                    }
                    tags {
                        tagGenerator = { url -> listOf(url.getOrNull(2)) }
                    }
                    schemas {
                        generator = { type ->
                            type
                                .analyzeTypeUsingKotlinxSerialization {

                                }
                                .addMissingSupertypeSubtypeRelations()
                                .addJsonClassDiscriminatorProperty()
                                .generateSwaggerSchema({
                                    strictDiscriminatorProperty = true
                                })
                                .customizeTypes { _, schema ->
                                    // Mapping is broken, and one of the code generation libraries I am using checks the
                                    // references here
                                    schema.discriminator?.mapping = null;
                                }
                                .withTitle(TitleType.SIMPLE)
                                .compileHybridInline(false, TitleBuilder.BUILDER_OPENAPI_SIMPLE)
                        }
                    }
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
                json(json, contentType = ContentType.Application.Json)
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

                    call.respondText(text = json.encodeToString(wrapped), status = wrapped.status)
                }
            }
            routing {
                debugRoutes(sessionManager)
                sessionRoutes(appConfig, sessionManager, devmode)
                messageRoutes(mcpServersByTransportId, sessionManager)
                telemetryRoutes(sessionManager)
                documentationRoutes()
                sseRoutes(mcpServersByTransportId, sessionManager)
                agentRoutes(appConfig, sessionManager)

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
        logger.info { "Starting sse server on port $port with ${appConfig.config.applications.size} configured applications" }
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");

        if (devmode) {
            logger.info {
                "In development, agents can connect to " +
                        "http://localhost:$port/sse/v1/exampleApplicationId/examplePrivacyKey/exampleSessionId/sse?agentId=exampleAgent"
            }
            logger.info {
                "Connect the inspector to " +
                        "http://localhost:$port/sse/v1/devmode/exampleApplicationId/examplePrivacyKey/exampleSessionId/sse?agentId=inspector"
            }
        }
        server.monitor.subscribe(ApplicationStarted) {
            logger.info { "Server started on $host:$port" }
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
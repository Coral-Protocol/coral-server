package org.coralprotocol.coralserver.modules.ktor

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
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.config.AuthConfig
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.events.LocalSessionManagerEvent
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.mcp.McpResourceName
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.routes.api.v1.*
import org.coralprotocol.coralserver.routes.sse.v1.mcpRoutes
import org.coralprotocol.coralserver.routes.ui.consoleUi
import org.coralprotocol.coralserver.routes.ui.documentationInterface
import org.coralprotocol.coralserver.routes.ws.v1.eventRoutes
import org.coralprotocol.coralserver.server.AuthSession
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionException
import org.koin.ktor.ext.inject
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

fun Application.coralServerModule() {
    val networkConfig by inject<NetworkConfig>()
    val authConfig by inject<AuthConfig>()
    val localSessionManager by inject<LocalSessionManager>()
    val json by inject<Json>()

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
            schema<McpResourceName>("McpResourceName")

            // WebSocket types
            schema<LocalSessionManagerEvent>("LocalSessionManagerEvent")
            schema<SessionEvent>("SessionEvent")
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
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true

        if (networkConfig.allowAnyHost)
            anyHost()
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Other exceptions should still be serialized, wrap non RouteException type exceptions in a
            // RouteException, giving a 500-status code
            val routeException = if (cause !is RouteException) {
                logger.error(cause) { "Unexpected exception thrown from route ${call.request.uri}" }
                RouteException(HttpStatusCode.InternalServerError, cause)
            } else {
                cause
            }

            call.respond(routeException.status, routeException)
        }
    }
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val response = call.response.status()
            if (response != null) {
                "${call.request.httpMethod} ${call.request.uri} - $response"
            } else {
                "${call.request.httpMethod} ${call.request.uri}"
            }
        }
    }
    install(Authentication) {
        bearer("token") {
            authenticate { credential ->
                if (!authConfig.keys.contains(credential.token))
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

        session<AuthSession.Token>("authSessionToken") {
            validate {
                authConfig.keys.contains(it.token)
            }
        }
    }
    install(Sessions) {
        cookie<AuthSession.Token>("authSessionToken", SessionStorageMemory()) {
            // todo: https
            //cookie.secure = true
        }
    }
    routing {
        authenticate("token", "authSessionToken") {
            sessionApi()
            registryApi()
            puppetApi()
        }

        authenticate("agentSecret") {
            agentRpcApi()
        }

        // safe interfaces, not subject to auth
        agentRentalApi()
        documentationInterface()

        // custom auth
        authApi()

        // url-based auth
        mcpRoutes()
        eventRoutes()

        // source of truth for OpenAPI docs/codegen
        route("api_v1.json") { openApi("v1") }
        route("ui") { consoleUi() }
    }.run {
        getAllRoutes().forEach {
            logger.info {
                "${it.selector} ${it.parent}"
            }
        }
    }
}
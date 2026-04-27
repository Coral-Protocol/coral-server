package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_ROUTES
import org.coralprotocol.coralserver.routes.ApiV1
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionIdentifier
import org.coralprotocol.coralserver.session.SessionRequest
import org.coralprotocol.coralserver.template.SessionTemplateInfo
import org.coralprotocol.coralserver.template.SessionTemplateRegistry
import org.coralprotocol.coralserver.template.TemplateLaunchRequest
import org.coralprotocol.coralserver.template.parseRegistrySource
import org.coralprotocol.coralserver.template.parseRuntimeId
import org.coralprotocol.coralserver.template.validateTemplateParameters
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject

@Resource("templates")
class Templates(val parent: ApiV1 = ApiV1()) {
    @Resource("{slug}")
    class BySlug(val parent: Templates = Templates(), val slug: String) {
        @Resource("launch")
        class Launch(val parent: BySlug)

        @Resource("preview")
        class Preview(val parent: BySlug)
    }
}

fun Route.templateApi() {
    val templateRegistry by inject<SessionTemplateRegistry>()
    val agentRegistry by inject<AgentRegistry>()
    val localSessionManager by inject<LocalSessionManager>()
    val logger by inject<Logger>(named(LOGGER_ROUTES))

    get<Templates>({
        summary = "List session templates"
        description = "Returns all available session templates"
        operationId = "listTemplates"
        securitySchemeNames("token")
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<SessionTemplateInfo>> {
                    description = "List of available templates"
                }
            }
        }
    }) {
        call.respond(templateRegistry.list())
    }

    get<Templates.BySlug>({
        summary = "Get session template"
        description = "Returns a specific session template with its parameter definitions"
        operationId = "getTemplate"
        securitySchemeNames("token")
        request {
            pathParameter<String>("slug") {
                description = "The template slug identifier"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<SessionTemplateInfo> {
                    description = "Template details"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Template not found"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) { path ->
        val template = templateRegistry.get(path.slug)
            ?: throw RouteException(HttpStatusCode.NotFound, "Template '${path.slug}' not found")
        call.respond(template.info)
    }

    post<Templates.BySlug.Launch>({
        summary = "Launch session from template"
        description = "Creates a session using the specified template"
        operationId = "launchTemplate"
        securitySchemeNames("token")
        request {
            pathParameter<String>("slug") {
                description = "The template slug identifier"
            }
            body<TemplateLaunchRequest> {
                description = "Template parameters and namespace"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Session created"
                body<SessionIdentifier> {
                    description = "Session details"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Template not found"
                body<RouteException> {
                    description = "Error message"
                }
            }
            HttpStatusCode.BadRequest to {
                description = "Invalid parameters"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) { path ->
        val template = templateRegistry.get(path.parent.slug)
            ?: throw RouteException(HttpStatusCode.NotFound, "Template '${path.parent.slug}' not found")
        val launchRequest = call.receive<TemplateLaunchRequest>()

        val sessionRequest = try {
            validateTemplateParameters(launchRequest.parameters, template.info)
            template.buildSessionRequest(
                launchRequest.parameters,
                launchRequest.namespace,
                parseRegistrySource(launchRequest.registrySource),
                parseRuntimeId(launchRequest.runtime),
            )
        } catch (e: IllegalStateException) {
            throw RouteException(HttpStatusCode.BadRequest, e.message ?: "Invalid parameters")
        } catch (e: IllegalArgumentException) {
            throw RouteException(HttpStatusCode.BadRequest, e.message ?: "Invalid parameters")
        }

        call.respond(
            resolveAndLaunchSession(
                sessionRequest = sessionRequest,
                agentRegistry = agentRegistry,
                localSessionManager = localSessionManager,
                logger = logger,
                logPrefix = "template '${path.parent.slug}'",
            )
        )
    }

    post<Templates.BySlug.Preview>({
        summary = "Preview session request from template"
        description = "Returns the SessionRequest that would be created by the specified template"
        operationId = "previewTemplate"
        securitySchemeNames("token")
        request {
            pathParameter<String>("slug") {
                description = "The template slug identifier"
            }
            body<TemplateLaunchRequest> {
                description = "Template parameters and namespace"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Generated session request"
                body<SessionRequest> {
                    description = "The generated session request"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Template not found"
                body<RouteException> {
                    description = "Error message"
                }
            }
            HttpStatusCode.BadRequest to {
                description = "Invalid parameters"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) { path ->
        val template = templateRegistry.get(path.parent.slug)
            ?: throw RouteException(HttpStatusCode.NotFound, "Template '${path.parent.slug}' not found")
        val launchRequest = call.receive<TemplateLaunchRequest>()

        val sessionRequest = try {
            validateTemplateParameters(launchRequest.parameters, template.info)
            template.buildSessionRequest(
                launchRequest.parameters,
                launchRequest.namespace,
                parseRegistrySource(launchRequest.registrySource),
                parseRuntimeId(launchRequest.runtime),
            )
        } catch (e: IllegalStateException) {
            throw RouteException(HttpStatusCode.BadRequest, e.message ?: "Invalid parameters")
        } catch (e: IllegalArgumentException) {
            throw RouteException(HttpStatusCode.BadRequest, e.message ?: "Invalid parameters")
        }

        call.respond(sessionRequest)
    }
}

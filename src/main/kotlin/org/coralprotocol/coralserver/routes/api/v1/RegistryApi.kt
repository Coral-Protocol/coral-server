package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.server.RouteException

@Resource("registry")
class Registry {
    @Resource("local/{agentName}/{agentVersion}")
    class Local(val parent: Registry = Registry(), val agentName: String, val agentVersion: String)

    @Resource("marketplace/{agentName}/{agentVersion}")
    class Marketplace(val parent: Registry = Registry(), val agentName: String, val agentVersion: String)

    @Resource("linked/{linkedServerName}/{agentName}/{agentVersion}")
    class Linked(
        val parent: Registry = Registry(),
        val linkedServerName: String,
        val agentName: String,
        val agentVersion: String
    )
}

fun Route.registryApi(registry: AgentRegistry) {
    get<Registry>({
        summary = "Get registry agents"
        description = "Returns a list of all agents available in this servers registry"
        operationId = "getRegistryAgents"
        securitySchemeNames("token")
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<AgentRegistrySource>> {
                    description = "A list of registry sources with compress agent catalogs"
                }
            }
        }
    }) {
        call.respond(HttpStatusCode.OK, registry.mergedSources)
    }

    get<Registry.Local>({
        summary = "Inspect local registry agent"
        description = "Returns all details about a specific agent in the local registry"
        operationId = "inspectLocalAgent"
        securitySchemeNames("token")
        request {
            pathParameter<String>("agentName") {
                description = "The name of the agent"
            }
            pathParameter<String>("agentVersion") {
                description = "The version of the agent"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<RestrictedRegistryAgent> {
                    description = "Local registry agent details"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Agent not found"
                body<RouteException> {
                    description = "Error details"
                }
            }
        }
    }) {
        resolveAgent(registry, it.agentName, it.agentVersion, AgentRegistrySourceIdentifier.Local)
    }

    get<Registry.Marketplace>({
        summary = "Inspect marketplace registry agent"
        description = "Returns all details about a specific agent in the marketplace"
        operationId = "inspectMarketplaceAgent"
        securitySchemeNames("token")
        request {
            pathParameter<String>("agentName") {
                description = "The name of the agent"
            }
            pathParameter<String>("agentVersion") {
                description = "The version of the agent"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<RestrictedRegistryAgent> {
                    description = "Marketplace registry agent details"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Agent not found"
                body<RouteException> {
                    description = "Error details"
                }
            }
        }
    }) {
        resolveAgent(registry, it.agentName, it.agentVersion, AgentRegistrySourceIdentifier.Marketplace)
    }

    get<Registry.Linked>({
        summary = "Inspect linked server registry agent"
        description = "Returns all details about a specific agent from a specific linked server"
        operationId = "inspectLinkedServerAgent"
        request {
            pathParameter<String>("linkedServerName") {
                description = "The name of the linked server to source the details of this agent from"
            }
            pathParameter<String>("agentName") {
                description = "The name of the agent"
            }
            pathParameter<String>("agentVersion") {
                description = "The version of the agent"
            }
        }
        securitySchemeNames("token")
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<RestrictedRegistryAgent> {
                    description = "Linked server registry agent details"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Agent or linked server not found"
                body<RouteException> {
                    description = "Error details"
                }
            }
        }
    }) {
        resolveAgent(registry, it.agentName, it.agentVersion, AgentRegistrySourceIdentifier.Linked(it.linkedServerName))
    }
}

private suspend fun RoutingContext.resolveAgent(
    registry: AgentRegistry,
    name: String,
    version: String,
    source: AgentRegistrySourceIdentifier
) {
    try {
        call.respond(
            HttpStatusCode.OK,
            registry.resolveAgent(
                RegistryAgentIdentifier(
                    name,
                    version,
                    source
                )
            )
        )
    } catch (e: RegistryException.AgentNotFoundException) {
        throw RouteException(HttpStatusCode.NotFound, e)
    } catch (e: RegistryException.RegistrySourceNotFoundException) {
        throw RouteException(HttpStatusCode.NotFound, e)
    }
}
package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject
import org.coralprotocol.coralserver.llm.LlmProxyService
import org.coralprotocol.coralserver.session.SessionAgent

@Resource("/llm-proxy/v1/chat/completions")
class LlmProxyChatCompletions

@Resource("/llm-proxy/v1/engines/{engineId}/chat/completions")
class LlmProxyEngineChatCompletions(val engineId: String)

fun Route.llmProxyApiRoutes(llmProxyService: LlmProxyService) {
    post<LlmProxyChatCompletions>({
        summary = "LLM Proxy Chat Completions"
        description = "Proxies OpenAI-compatible chat completion requests, authorized by agent secret"
        operationId = "llmProxyChatCompletions"
        securitySchemeNames("agentSecret")
        request {
            body<JsonObject> {
                description = "OpenAI-compatible chat completion request"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<JsonObject>()
            }
        }
    }) {
        val agent = call.principal<SessionAgent>()
            ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val requestBody = call.receive<JsonObject>()
        val response = llmProxyService.proxyChatCompletion(agent, requestBody)

        call.respondText(response.bodyAsText(), contentType = ContentType.Application.Json, status = response.status)
    }

    post<LlmProxyEngineChatCompletions>({
        summary = "LLM Proxy Chat Completions (with engine)"
        description = "Proxies OpenAI-compatible chat completion requests with a prescribed engine, authorized by agent secret"
        operationId = "llmProxyEngineChatCompletions"
        securitySchemeNames("agentSecret")
        request {
            pathParameter<String>("engineId") {
                description = "The engine ID to use"
            }
            body<JsonObject> {
                description = "OpenAI-compatible chat completion request"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<JsonObject>()
            }
        }
    }) { params ->
        val agent = call.principal<SessionAgent>()
            ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val requestBody = call.receive<JsonObject>()
        val response = llmProxyService.proxyChatCompletion(agent, requestBody, params.engineId)

        call.respondText(response.bodyAsText(), contentType = ContentType.Application.Json, status = response.status)
    }
}

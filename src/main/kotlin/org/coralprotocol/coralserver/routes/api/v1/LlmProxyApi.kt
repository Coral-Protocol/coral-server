package org.coralprotocol.coralserver.routes.api.v1

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIAudioConfig
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMRequest
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIModalities
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIResponseFormat
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStaticContent
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIStreamOptions
import ai.koog.prompt.executor.clients.openai.base.models.OpenAITool
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolChoice
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIWebSearchOptions
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.base.models.ServiceTier
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.llm.LlmProxyService
import org.coralprotocol.coralserver.models.OpenAiModelList
import org.coralprotocol.coralserver.models.Telemetry
import org.coralprotocol.coralserver.models.TelemetryMessages
import org.coralprotocol.coralserver.models.telemetry.generic.ImageDetail
import org.coralprotocol.coralserver.models.telemetry.openai.*
import org.coralprotocol.coralserver.models.telemetry.openai.Message as TelemetryOpenAIMessage
import org.coralprotocol.coralserver.session.SessionAgent

@Resource("/llm-proxy/v1/chat/completions")
class LlmProxyChatCompletions

@Resource("/llm-proxy/v1/models/{modelId}/chat/completions")
class LlmProxyModelChatCompletions(val modelId: String)

@Resource("/llm-proxy/v1/models")
class LlmProxyModels

@Resource("/v1/chat/completions")
class OpenAiChatCompletions

@Resource("/v1/models")
class OpenAiModels

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}
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

    post<LlmProxyModelChatCompletions>({
        summary = "LLM Proxy Chat Completions (with model)"
        description = "Proxies OpenAI-compatible chat completion requests with a prescribed model, authorized by agent secret"
        operationId = "llmProxyModelChatCompletions"
        securitySchemeNames("agentSecret")
        request {
            pathParameter<String>("modelId") {
                description = "The model ID to use"
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
        val latestMessage = agent.getThreads().flatMap { it.messages }.maxByOrNull { it.timestamp }
        if (latestMessage != null) {
            try {
                val encodedRequest: OpenAIChatCompletionRequest = json.decodeFromJsonElement(requestBody)
                latestMessage.telemetry = encodedRequest.toTelemetry()
                println("Attached telemetry to latest message: ${latestMessage.id}")
            } catch (e: Exception) {
                // TODO: Better logging
                e.printStackTrace()
            }
        }
        val response = llmProxyService.proxyChatCompletion(agent, requestBody, params.modelId)

        call.respondText(response.bodyAsText(), contentType = ContentType.Application.Json, status = response.status)
    }

    get<LlmProxyModels>({
        summary = "LLM Proxy List Models"
        description = "Lists available models in the LLM proxy"
        operationId = "llmProxyListModels"
        securitySchemeNames("agentSecret")
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<OpenAiModelList>()
            }
        }
    }) {
        val agent = call.principal<SessionAgent>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)

        call.respond(llmProxyService.listModels())
    }

    post<OpenAiChatCompletions>({
        summary = "OpenAI Chat Completions"
        description = "Standard OpenAI-compatible chat completion endpoint"
        operationId = "openAiChatCompletions"
        securitySchemeNames("agentSecret")
        request {
            body<JsonObject>()
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
        val latestMessage = agent.getThreads().flatMap { it.messages }.maxByOrNull { it.timestamp }
        if(latestMessage != null) {
            try {
                val encodedRequest: OpenAIChatCompletionRequest = json.decodeFromJsonElement(requestBody)
                latestMessage.telemetry = encodedRequest.toTelemetry()
                println("Attached telemetry to latest message: ${latestMessage.id}")
            } catch (e: Exception) {
                // TODO: Better logging
                e.printStackTrace()
            }
        }

        val response = llmProxyService.proxyChatCompletion(agent, requestBody)

        call.respondText(response.bodyAsText(), contentType = ContentType.Application.Json, status = response.status)
    }

    get<OpenAiModels>({
        summary = "OpenAI List Models"
        description = "Standard OpenAI-compatible list models endpoint"
        operationId = "openAiListModels"
        securitySchemeNames("agentSecret")
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<OpenAiModelList>()
            }
        }
    }) {
        val agent = call.principal<SessionAgent>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)


        call.respond(llmProxyService.listModels())
    }
}

private fun OpenAIChatCompletionRequest.toTelemetry(): Telemetry {
    val telemetryMessages = this.messages.map { msg ->
        val msgJson = Json.encodeToJsonElement(msg).jsonObject
        val role = msgJson["role"]?.jsonPrimitive?.content
        val content = msgJson["content"]

        when (role) {
            "system", "developer" -> {
                val text = when (content) {
                    is JsonPrimitive -> content.content
                    is JsonArray -> content.joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
                    else -> ""
                }
                TelemetryOpenAIMessage.SystemMessage(
                    listOf(SystemContent(SystemContentType.TEXT, text)),
                    msgJson["name"]?.jsonPrimitive?.content
                )
            }
            "user" -> {
                val contents = mutableListOf<UserContent>()
                when (content) {
                    is JsonPrimitive -> contents.add(UserContent.Text(content.content))
                    is JsonArray -> content.forEach {
                        val c = it.jsonObject
                        when (c["type"]?.jsonPrimitive?.content) {
                            "text" -> contents.add(UserContent.Text(c["text"]?.jsonPrimitive?.content ?: ""))
                            "image_url" -> {
                                val imageUrlObj = c["image_url"]?.jsonObject
                                val url = imageUrlObj?.get("url")?.jsonPrimitive?.content ?: ""
                                val detailStr = imageUrlObj?.get("detail")?.jsonPrimitive?.content ?: "auto"
                                val detail = try {
                                    ImageDetail.valueOf(detailStr.uppercase())
                                } catch (e: Exception) {
                                    ImageDetail.AUTO
                                }
                                contents.add(UserContent.Image(ImageUrl(url, detail)))
                            }
                        }
                    }
                    else -> {}
                }
                TelemetryOpenAIMessage.UserMessage(contents, msgJson["name"]?.jsonPrimitive?.content)
            }
            "assistant" -> {
                val contents = mutableListOf<AssistantContent>()
                if (content is JsonPrimitive) {
                    contents.add(AssistantContent.Text(content.content))
                } else if (content is JsonArray) {
                    content.forEach {
                        val c = it.jsonObject
                        if (c["type"]?.jsonPrimitive?.content == "text") {
                            contents.add(AssistantContent.Text(c["text"]?.jsonPrimitive?.content ?: ""))
                        }
                    }
                }
                val toolCalls = msgJson["tool_calls"]?.jsonArray?.map {
                    val tc = it.jsonObject
                    val fn = tc["function"]?.jsonObject
                    ToolCall(
                        id = tc["id"]?.jsonPrimitive?.content ?: "",
                        type = ToolType.Function,
                        Function(
                            name = fn?.get("name")?.jsonPrimitive?.content ?: "",
                            arguments = fn?.get("arguments")?.jsonPrimitive?.content ?: ""
                        )
                    )
                } ?: emptyList()
                TelemetryOpenAIMessage.AssistantMessage(
                    contents,
                    refusal = msgJson["refusal"]?.jsonPrimitive?.content,
                    name = msgJson["name"]?.jsonPrimitive?.content,
                    toolCalls = toolCalls
                )
            }
            "tool" -> {
                TelemetryOpenAIMessage.ToolMessage(
                    msgJson["tool_call_id"]?.jsonPrimitive?.content ?: "",
                    listOf(ToolResultContent(ToolResultContentType.Text, content?.jsonPrimitive?.content ?: ""))
                )
            }
            else -> TelemetryOpenAIMessage.UserMessage(listOf(UserContent.Text(content?.jsonPrimitive?.content ?: content.toString())), msgJson["name"]?.jsonPrimitive?.content)
        }
    }

    return Telemetry(
        modelDescription = this.model,
        temperature = this.temperature,
        maxTokens = this.maxTokens?.toLong(),
        messages = TelemetryMessages.OpenAI(telemetryMessages),
        resources = emptyList(),
        tools = emptyList()
    )
}


// taken from koog
@Serializable
internal class OpenAIChatCompletionRequest(
    val messages: List<OpenAIMessage>,
    override val model: String,
    val audio: OpenAIAudioConfig? = null,
    val frequencyPenalty: Double? = null,
    val logitBias: Map<String, Int>? = null,
    val logprobs: Boolean? = null,
    val maxCompletionTokens: Int? = null,
    val maxTokens: Int? = null,
    val metadata: Map<String, String>? = null,
    val modalities: List<OpenAIModalities>? = null,
    @SerialName("n")
    val numberOfChoices: Int? = null,
    val parallelToolCalls: Boolean? = null,
    val prediction: OpenAIStaticContent? = null,
    val presencePenalty: Double? = null,
    val promptCacheKey: String? = null,
    val reasoningEffort: ReasoningEffort? = null,
    val responseFormat: OpenAIResponseFormat? = null,
    val safetyIdentifier: String? = null,
    val seed: Int? = null,
    val serviceTier: ServiceTier? = null,
    val stop: List<String>? = null,
    val store: Boolean? = null,
    override val stream: Boolean? = null,
    val streamOptions: OpenAIStreamOptions? = null,
    override val temperature: Double? = null,
    val toolChoice: OpenAIToolChoice? = null,
    val tools: List<OpenAITool>? = null,
    override val topLogprobs: Int? = null,
    override val topP: Double? = null,
    val user: String? = null,
    val webSearchOptions: OpenAIWebSearchOptions? = null,
    val additionalProperties: Map<String, JsonElement>? = null,
) : OpenAIBaseLLMRequest
package org.coralprotocol.coralserver.llm

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.config.RootConfig
import org.coralprotocol.coralserver.models.OpenAiModel
import org.coralprotocol.coralserver.models.OpenAiModelList
import org.coralprotocol.coralserver.models.Telemetry
import org.coralprotocol.coralserver.models.TelemetryMessages
import org.coralprotocol.coralserver.models.telemetry.openai.*
import org.coralprotocol.coralserver.models.telemetry.openai.Message as OpenAIMessage
import org.coralprotocol.coralserver.session.SessionAgent

class LlmProxyService(
    private val httpClient: HttpClient,
    private val json: Json
) {
    private val config by lazy { org.koin.core.context.GlobalContext.get().get<RootConfig>() }

    fun listModels(): OpenAiModelList {
        return OpenAiModelList(
            data = config.llmProxyConfig.models.keys.map { OpenAiModel(it) }
        )
    }

    suspend fun proxyChatCompletion(
        agent: SessionAgent,
        requestBody: JsonObject,
        modelIdOverride: String? = null
    ): HttpResponse {
        val model = modelIdOverride ?: requestBody["model"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing model in request")

        val modelConfig = config.llmProxyConfig.models[model]
            ?: throw IllegalArgumentException("Unknown model: $model")

        val providerConfig = config.llmProxyConfig.providers[modelConfig.provider]
            ?: throw IllegalArgumentException("Unknown provider: ${modelConfig.provider}")

        val targetUrl = "${providerConfig.baseUrl ?: "https://api.openai.com/v1"}/chat/completions"

        val timeoutSeconds = providerConfig.timeoutSeconds ?: config.llmProxyConfig.requestTimeoutSeconds

        val response = httpClient.post(targetUrl) {
            timeout {
                requestTimeoutMillis = timeoutSeconds * 1000
                socketTimeoutMillis = timeoutSeconds * 1000
            }
            header(HttpHeaders.Authorization, "Bearer ${providerConfig.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                requestBody.forEach { (key, value) ->
                    if (key == "model") {
                        put("model", modelConfig.model)
                    } else {
                        put(key, value)
                    }
                }
            })
        }

        if (response.status.isSuccess()) {
            val responseBody = response.bodyAsText()
            try {
                val responseJson = json.parseToJsonElement(responseBody).jsonObject
                val telemetry = buildTelemetry(requestBody, responseJson, modelConfig.model)
                agent.recordTelemetry(telemetry)
            } catch (e: Exception) {
                agent.logger.error(e) { "Failed to record telemetry for LLM proxy call" }
            }
        }

        return response
    }

    private fun buildTelemetry(
        request: JsonObject,
        response: JsonObject,
        actualModel: String
    ): Telemetry {
        val messages = mutableListOf<OpenAIMessage>()

        request["messages"]?.jsonArray?.forEach { msg ->
            val obj = msg.jsonObject
            val role = obj["role"]?.jsonPrimitive?.content
            val content = obj["content"]?.jsonPrimitive?.content ?: ""

            when (role) {
                "system", "developer" -> messages.add(
                    OpenAIMessage.SystemMessage(
                        listOf(SystemContent(SystemContentType.TEXT, content)),
                        obj["name"]?.jsonPrimitive?.content
                    )
                )

                "user" -> messages.add(
                    OpenAIMessage.UserMessage(
                        listOf(UserContent.Text(content)),
                        obj["name"]?.jsonPrimitive?.content
                    )
                )

                "assistant" -> messages.add(
                    OpenAIMessage.AssistantMessage(
                        listOf(AssistantContent.Text(content)),
                        toolCalls = emptyList()
                    )
                )

                "tool" -> messages.add(
                    OpenAIMessage.ToolMessage(
                        obj["tool_call_id"]?.jsonPrimitive?.content ?: "",
                        listOf(ToolResultContent(ToolResultContentType.Text, content))
                    )
                )
            }
        }

        // Add assistant's response
        response["choices"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("message")?.jsonObject?.let { msg ->
            val content = msg["content"]?.jsonPrimitive?.content ?: ""
            messages.add(
                OpenAIMessage.AssistantMessage(
                    listOf(AssistantContent.Text(content)),
                    toolCalls = emptyList()
                )
            )
        }

        return Telemetry(
            modelDescription = actualModel,
            temperature = request["temperature"]?.jsonPrimitive?.doubleOrNull,
            maxTokens = request["max_tokens"]?.jsonPrimitive?.longOrNull,
            messages = TelemetryMessages.OpenAI(messages),
            resources = emptyList(),
            tools = emptyList()
        )
    }
}

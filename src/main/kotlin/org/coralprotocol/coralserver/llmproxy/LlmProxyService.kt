@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.llmproxy

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.agent.registry.AgentLlmProxyRequest
import org.coralprotocol.coralserver.config.CloudConfig
import org.coralprotocol.coralserver.config.LlmProxyConfig
import org.coralprotocol.coralserver.config.LlmProxyProviderConfig
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_LLM_PROXY
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.SessionAgent
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.qualifier.named
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val ALLOWED_METHODS = setOf(HttpMethod.Get, HttpMethod.Post)
private val METHODS_WITH_BODY = setOf(HttpMethod.Post)

@Serializable
@JsonIgnoreUnknownKeys
private data class OpenAIModelList(
    @SerialName("data") val models: List<OpenAIModel>
    // .. etc
)

@Serializable
@JsonIgnoreUnknownKeys
private data class OpenAIModel(
    val id: String,
    // .. etc
)

class LlmProxyService(
    private val llmProxyConfig: LlmProxyConfig,
    private val cloudConfig: CloudConfig,
    private val httpClient: HttpClient,
    private val json: Json,
    logger: Logger
) {
    companion object : KoinComponent {
        fun buildCoralCloudProviders(apiKey: String): List<LlmProxyProviderConfig> {
            val httpClient = get<HttpClient>()
            val logger = get<Logger>(named(LOGGER_LLM_PROXY))

            return buildList {
                add(
                    LlmProxyProviderConfig(
                        name = "Coral Cloud, OpenAI",
                        format = LlmProviderFormat.OpenAI,
                        models = runBlocking {
                            try {
                                httpClient.get("https://llm.coralcloud.ai/openai/v1/models") {
                                    bearerAuth(apiKey)
                                }.body<OpenAIModelList>().models.map { it.id }.toSet()
                            } catch (e: Exception) {
                                logger.error(e) { "Failed to fetch Coral Cloud OpenAI models" }
                                emptySet()
                            }
                        },
                        apiKey = apiKey,
                        baseUrl = "https://llm.coralcloud.ai/openai/",
                        timeout = 10.seconds,
                        allowAnyModel = false
                    )
                )
            }
        }
    }

    val providers = buildList {
        addAll(llmProxyConfig.providers)

        if (cloudConfig.apiKey != null)
            addAll(buildCoralCloudProviders(cloudConfig.apiKey))

    }.toMutableList()

    init {
        if (!providers.any { it.format == LlmProviderFormat.OpenAI })
            logger.warn { "The server will not be able to launch agents that require OpenAI-format LLM proxies as no provider of this format has been configured" }

        if (!providers.any { it.format == LlmProviderFormat.Anthropic })
            logger.warn { "The server will not be able to launch agents that require Anthropic-format LLM proxies as no provider of this format has been configured" }
    }

    /**
     * Attempts to resolve an agent's request for a proxy, returning a [LlmProxiedModel] that contains a proxy config
     * for the requested format and models.  This function will throw an exception if the request cannot be resolved.
     *
     * @throws LlmProxyException.ProxyRequestResolutionError if the request cannot be resolved
     */
    fun resolveAgentProxyRequest(request: AgentLlmProxyRequest): LlmProxiedModel {
        val potentialProviders = providers.filter { it.format == request.format }
        if (potentialProviders.isEmpty())
            throw LlmProxyException.ProxyRequestResolutionError("No providers are configured for format \"${request.format}\".")

        var match = potentialProviders.firstNotNullOfOrNull { provider ->
            provider.models.firstOrNull { request.models.contains(it) }?.let { it to provider }
        }

        // Fallback: check for providers that will provide any of the requested models
        if (match == null) {
            match = potentialProviders.filter {
                it.allowAnyModel
            }.firstNotNullOfOrNull { provider ->
                request.models.firstOrNull()?.let { it to provider }
            }
        }

        if (match == null)
            throw LlmProxyException.ProxyRequestResolutionError("None of the ${potentialProviders.size} configured \"${request.format}\" providers support any of the requested models: ${request.models.joinToString()}")

        return LlmProxiedModel(match.second, match.first)
    }

    /**
     * Methods: GET, POST
     * POST body: JSON only (application/json and application/+json)
     * Responses: JSON or SSE
     * Forwarded: path, query params, provider auth, most normal headers
     * Not supported: multipart, binary uploads, file/audio/image upload endpoints
     * Current scope: inference-style endpoints like chat/messages/responses/embeddings/models
     * Security behavior: Authorization/provider auth is normalized by the proxy, Cookie is dropped
     */
    suspend fun proxyRequest(
        agent: SessionAgent,
        model: LlmProxiedModel,
        pathParts: List<String>,
        call: ApplicationCall
    ) {
        validateRequestShape(call)

        val upstreamUrl = URLBuilder(model.providerConfig.baseUrl).apply {
            appendEncodedPathSegments(pathParts)
            call.request.queryParameters.entries().forEach { (name, values) ->
                values.forEach { value -> parameters.append(name, value) }
            }
        }.buildString()

        val hasBody = call.request.httpMethod in METHODS_WITH_BODY
        val requestBody = readRequestBody(hasBody, call)

        val requestJson = if (hasBody) tryParseJson(requestBody) else null
        val isStreaming = requestJson?.get("stream")?.jsonPrimitive?.booleanOrNull == true
        val requestedModel = requestJson?.get("model")?.jsonPrimitive?.content

        if (requestedModel != null && requestedModel != model.modelName)
            agent.logger.warn { "LLM Proxy received request for ${model.modelName}, this will be substituted with ${model.modelName}" }

        val finalBody =
            if (isStreaming) model.providerConfig.format.prepareStreamingRequest(
                requestBody,
                json,
                agent.logger
            ) else requestBody

        val req = LlmProxyRequest(
            model = model,
            agent = agent,
            requestBody = finalBody,
            hasBody = hasBody,
            upstreamUrl = upstreamUrl,
            startTime = Clock.System.now()
        )

        agent.logger.debug {
            "LLM Proxy → $model/${
                URLBuilder().appendPathSegments(pathParts).buildString()
            } model=$model streaming=$isStreaming "
        }

        try {
            if (isStreaming) proxyStreaming(req, call) else proxyBuffered(req, call)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emitTelemetry(
                agent,
                LlmCallResult(
                    req,
                    streaming = isStreaming,
                    success = false,
                    errorKind = classifyError(e)
                )
            )
            if (!call.response.isCommitted) {
                agent.logger.warn { "LLM proxy request failed: ${e::class.simpleName}: ${e.message}" }
                throw RouteException(HttpStatusCode.BadGateway, "LLM proxy request failed")
            }
        }
    }

    private suspend fun proxyBuffered(req: LlmProxyRequest, call: ApplicationCall) {
        val response = httpClient.request(req.upstreamUrl) {
            configureProxy(req, call)
        }

        val responseBody = readBoundedBody(response)
        val endTime = Clock.System.now()

        LlmProxyHeaders.forwardResponseHeaders(response, call)
        val upstreamContentType = response.contentType() ?: ContentType.Application.Json
        call.respondText(responseBody, upstreamContentType, response.status)

        val usage = req.model.providerConfig.format.extractBufferedTokens(responseBody, json)
        emitTelemetry(
            req.agent,
            LlmCallResult(
                request = req,
                inputTokens = usage?.inputTokens,
                outputTokens = usage?.outputTokens,
                streaming = false,
                success = response.status.isSuccess(),
                errorKind = if (response.status.isSuccess()) null else classifyHttpError(response.status),
                statusCode = response.status.value,
                endTime = endTime,
            )
        )
    }

    private suspend fun proxyStreaming(req: LlmProxyRequest, call: ApplicationCall) {
        httpClient.prepareRequest(req.upstreamUrl) {
            configureProxy(req, call)
            timeout {
                socketTimeoutMillis = req.model.providerConfig.timeout.inWholeMilliseconds
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()

                val upstreamContentType = response.contentType() ?: ContentType.Application.Json
                call.respondText(errorBody, upstreamContentType, response.status)
                emitTelemetry(
                    req.agent,
                    LlmCallResult(
                        request = req,
                        streaming = true,
                        success = false,
                        errorKind = classifyHttpError(response.status),
                        statusCode = response.status.value
                    )
                )
                return@execute
            }

            call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.response.header("X-Accel-Buffering", "no")

            call.respondTextWriter {
                val channel = response.bodyAsChannel()
                val parser = req.model.providerConfig.format.createStreamParser(json)
                var totalChars = 0L

                try {
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        totalChars += line.length + 1
                        if (totalChars > llmProxyConfig.maxStreamChars.inWholeBytes) {
                            emitTelemetry(
                                req.agent,
                                LlmCallResult(
                                    request = req,
                                    streaming = true,
                                    success = false,
                                    errorKind = LlmErrorKind.RESPONSE_TOO_LARGE,
                                    statusCode = response.status.value
                                )
                            )
                            break
                        }
                        parser.processLine(line)
                        write(line)
                        write("\n")
                        flush()
                    }

                    if (totalChars <= llmProxyConfig.maxStreamChars.inWholeBytes) {
                        emitTelemetry(
                            req.agent,
                            LlmCallResult(
                                request = req,
                                inputTokens = parser.inputTokens,
                                outputTokens = parser.outputTokens,
                                streaming = true,
                                success = true,
                                statusCode = response.status.value,
                                chunkCount = parser.chunkCount
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emitTelemetry(
                        req.agent,
                        LlmCallResult(
                            request = req,
                            inputTokens = parser.inputTokens,
                            outputTokens = parser.outputTokens,
                            streaming = true,
                            success = false,
                            errorKind = classifyError(e),
                            statusCode = response.status.value
                        )
                    )
                }
            }
        }
    }

    private fun HttpRequestBuilder.configureProxy(req: LlmProxyRequest, call: ApplicationCall) {
        method = call.request.httpMethod

        timeout {
            requestTimeoutMillis = req.model.providerConfig.timeout.inWholeMilliseconds
        }

        LlmProxyHeaders.applyUpstream(this, call, req)

        if (req.hasBody) {
            contentType(call.request.contentType())
            setBody(req.requestBody)
        }
    }

    private fun validateRequestShape(call: ApplicationCall) {
        val method = call.request.httpMethod
        if (method !in ALLOWED_METHODS) {
            throw RouteException(HttpStatusCode.MethodNotAllowed, "Unsupported proxy method: $method")
        }

        if (method in METHODS_WITH_BODY && !isSupportedJsonContentType(call.request.contentType())) {
            throw RouteException(
                HttpStatusCode.UnsupportedMediaType,
                "LLM proxy only supports JSON request bodies"
            )
        }
    }

    private fun isSupportedJsonContentType(contentType: ContentType): Boolean {
        val normalized = contentType.withoutParameters()
        return normalized.match(ContentType.Application.Json) ||
                (normalized.contentType == "application" && normalized.contentSubtype.endsWith("+json"))
    }

    private suspend fun readRequestBody(hasBody: Boolean, call: ApplicationCall): String {
        if (!hasBody) return ""
        val channel = call.receiveChannel()
        val body = channel.readRemaining(llmProxyConfig.maxRequestSize.inWholeBytes).readText()

        if (channel.availableForRead > 0 || !channel.isClosedForRead)
            throw LlmProxyException.BufferOverflow("Upstream response exceeded ${llmProxyConfig.maxRequestSize} limit")

        return body
    }

    private fun tryParseJson(body: String): JsonObject? {
        return try {
            json.decodeFromString<JsonObject>(body)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun readBoundedBody(response: HttpResponse): String {
        val channel = response.bodyAsChannel()
        val body = channel.readRemaining(llmProxyConfig.maxResponseSize.inWholeBytes).readText()

        if (channel.availableForRead > 0 || !channel.isClosedForRead)
            throw LlmProxyException.BufferOverflow("Upstream response exceeded ${llmProxyConfig.maxResponseSize} limit")

        return body
    }

    private fun classifyHttpError(status: HttpStatusCode): LlmErrorKind = when (status.value) {
        429 -> LlmErrorKind.RATE_LIMITED
        in 401..403 -> LlmErrorKind.CREDENTIALS
        in 500..599 -> LlmErrorKind.UPSTREAM_HEALTH
        in 400..499 -> LlmErrorKind.REQUEST_ERROR
        else -> LlmErrorKind.UNKNOWN
    }

    private fun classifyError(e: Exception): LlmErrorKind = when (e) {
        is ConnectTimeoutException -> LlmErrorKind.CONNECTIVITY
        is HttpRequestTimeoutException -> LlmErrorKind.CONNECTIVITY
        is LlmProxyException -> LlmErrorKind.RESPONSE_TOO_LARGE
        else -> if (e.message?.contains(
                "timeout",
                ignoreCase = true
            ) == true
        ) LlmErrorKind.CONNECTIVITY else LlmErrorKind.UNKNOWN
    }

    private suspend fun emitTelemetry(agent: SessionAgent, result: LlmCallResult) {
        try {
            if (result.success) {
                val chunks = if (result.chunkCount != null) " ${result.chunkCount} chunks" else ""
                val mode = if (result.streaming) "stream complete" else "${result.statusCode ?: "ok"}"
                agent.logger.debug { "LLM Proxy ← $mode ${result.duration} $chunks${result.formatTokenInfo()}" }
            } else {
                val mode = if (result.streaming) " (stream)" else ""
                agent.logger.warn { "LLM Proxy ← ${result.statusCode ?: "err"} ${result.duration} error=${result.errorKind}$mode" }
            }

            agent.session.events.emit(
                SessionEvent.LlmProxyCall(
                    agentName = agent.name,
                    provider = result.request.model.providerConfig.name,
                    model = result.request.model.modelName,
                    inputTokens = result.inputTokens,
                    outputTokens = result.outputTokens,
                    duration = result.duration,
                    streaming = result.streaming,
                    success = result.success,
                    errorKind = result.errorKind
                )
            )
        } catch (e: Exception) {
            agent.logger.error(e) { "Failed to emit LLM proxy telemetry event" }
        }
    }
}

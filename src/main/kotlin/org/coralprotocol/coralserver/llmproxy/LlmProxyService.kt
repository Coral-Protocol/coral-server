@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.llmproxy

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import me.saket.bytesize.BinaryByteSize
import me.saket.bytesize.ByteSize
import org.coralprotocol.coralserver.agent.graph.GraphAgentProxyRequest
import org.coralprotocol.coralserver.agent.registry.AgentLlmProxyRequest
import org.coralprotocol.coralserver.config.CloudConfig
import org.coralprotocol.coralserver.config.LlmProxyConfig
import org.coralprotocol.coralserver.config.LlmProxyProviderConfig
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.logging.LoggingTag
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.SessionAgent
import java.io.ByteArrayOutputStream
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
    private val maxRequestSize = llmProxyConfig.maxRequestSize
    private val maxResponseSize = llmProxyConfig.maxResponseSize
    private val maxStreamChars = llmProxyConfig.maxStreamChars

    companion object {
        fun buildCoralCloudProviders(apiKey: String, json: Json = Json): List<LlmProxyProviderConfig> {
            val client = HttpClient(CIO) {
                install(Resources)
                install(ContentNegotiation) {
                    json(json)
                }
            }

            return buildList {
                add(
                    LlmProxyProviderConfig(
                        name = "Coral Cloud, OpenAI",
                        format = LlmProviderFormat.OpenAI,
                        models = runBlocking {
                            client.get("https://llm.coralcloud.ai/openai/v1/models") {
                                bearerAuth(apiKey)
                            }.body<OpenAIModelList>().models.map { it.id }.toSet()
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

        if (cloudConfig.apiKey != null) {
            try {
                addAll(buildCoralCloudProviders(cloudConfig.apiKey, json))
            } catch (e: Exception) {
                logger.error(e) { "Failed to fetch Coral Cloud OpenAI models" }
            }
        }

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
     * Resolves a direct request for a proxy and model using the [GraphAgentProxyRequest] type.  This type specifies
     * exact configurations and models to use, instead of [AgentLlmProxyRequest] which will select any matching provider
     * and config combination.
     *
     * @throws LlmProxyException.ProxyRequestResolutionError if the request cannot be resolved
     */
    fun resolveAgentProxyRequest(request: GraphAgentProxyRequest): LlmProxiedModel {
        val config = providers.firstOrNull { it.name == request.configurationName }
            ?: throw LlmProxyException.ProxyRequestResolutionError("No proxy is configured with the name \"${request.configurationName}\"")

        return LlmProxiedModel(config, request.modelName)
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
        proxyRequestName: String,
        pathParts: List<String>,
        call: ApplicationCall
    ) {
        validateRequestShape(call)

        val model = agent.graphAgent.proxies[proxyRequestName] ?: throw RouteException(
            HttpStatusCode.BadRequest, "Unknown proxy name"
        )

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

        val finalBody =
            if (isStreaming) model.providerConfig.format.prepareStreamingRequest(
                requestBody,
                json,
                agent.logger
            ) else requestBody

        val req = LlmProxyRequest(
            logger = agent.logger.withTags(LoggingTag.ProxyRequest(agent.proxyRequestCount.updateAndGet { it + 1 })),
            proxyRequestName = proxyRequestName,
            model = model,
            agent = agent,
            requestBody = finalBody,
            hasBody = hasBody,
            upstreamUrl = upstreamUrl,
            startTime = Clock.System.now(),
        )

        req.logger.info {
            "Proxy request started: config=\"${model.providerConfig.name}\", model=${model.modelName}, url=\"${req.upstreamUrl}\", method=${call.request.httpMethod}, streaming=$isStreaming"
        }

        if (requestedModel != null && requestedModel != model.modelName)
            req.logger.warn { "Request model is ${model.modelName}, this will be substituted with ${model.modelName}" }

        try {
            if (isStreaming) proxyStreaming(req, call) else proxyBuffered(req, call)
        } catch (e: CancellationException) {
            registerProxyResult(agent, LlmProxyResult.Exception(req, e))
            throw e
        } catch (e: Exception) {
            registerProxyResult(agent, LlmProxyResult.Exception(req, e))
        }
    }

    private suspend fun proxyBuffered(req: LlmProxyRequest, call: ApplicationCall) {
        val response = httpClient.request(req.upstreamUrl) {
            configureProxy(req, call)
        }

        val (responseBody, responseSize) = readBoundedBody(response)

        LlmProxyHeaders.forwardResponseHeaders(response, call)
        val upstreamContentType = response.contentType() ?: ContentType.Application.Json

        req.agent.logger.trace { "Proxy response received: ${response.status}, ${BinaryByteSize(responseSize)} of $upstreamContentType" }
        call.respondText(responseBody, upstreamContentType, response.status)

        registerProxyResult(
            req.agent, LlmProxyResult.Buffered(
                request = req,
                usage = req.model.providerConfig.format.extractBufferedTokens(responseBody, json),
                statusCode = response.status
            )
        )
    }

    /**
     * Reads from a [ByteReadChannel] into a [ByteArrayOutputStream] until a newline character is read.  The newline
     * will be included in [buffer].  This supports CRLF because it ends in a LF character.  In both cases, the newline
     * characters will be included in [buffer]
     */
    private suspend fun ByteReadChannel.readLineWithByteLimit(
        remainingLimit: ByteSize,
        totalLimit: ByteSize,
        buffer: ByteArrayOutputStream
    ): Long {
        var count = 0L

        while (!isClosedForRead) {
            if (count >= remainingLimit.inWholeBytes) {
                throw LlmProxyException.BufferOverflow("Upstream streamed response exceeded $totalLimit limit")
            }

            val byte = readByte().toInt()
            count++

            buffer.write(byte)
            if (byte == '\n'.code)
                break
        }

        return count
    }

    private suspend fun proxyStreaming(req: LlmProxyRequest, call: ApplicationCall) {
        httpClient.prepareRequest(req.upstreamUrl) {
            configureProxy(req, call)
            timeout {
                socketTimeoutMillis = req.model.providerConfig.timeout.inWholeMilliseconds
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                registerProxyResult(
                    req.agent, try {
                        val upstreamContentType = response.contentType() ?: ContentType.Application.Json

                        val (errorBody, errorSize) = readBoundedBody(response)
                        req.agent.logger.trace {
                            "Streamed proxy response received: ${response.status}, ${BinaryByteSize(errorSize)} of $upstreamContentType"
                        }
                        call.respondText(errorBody, upstreamContentType, response.status)

                        LlmProxyResult.Streamed(
                            request = req,
                            statusCode = response.status,
                            chunkCount = 0,
                        )
                    } catch (e: LlmProxyException) {
                        LlmProxyResult.Exception(
                            request = req,
                            error = e,
                        )
                    }
                )
            } else {
                call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.response.header("X-Accel-Buffering", "no")

                call.respondTextWriter {
                    val channel = response.bodyAsChannel()
                    val parser = req.model.providerConfig.format.createStreamParser(json)
                    var totalBytes = BinaryByteSize(0L)

                    try {
                        while (!channel.isClosedForRead) {
                            val remaining = maxStreamChars - totalBytes
                            val buffer = ByteArrayOutputStream()
                            val lineSize = BinaryByteSize(
                                channel.readLineWithByteLimit(
                                    remaining,
                                    maxStreamChars,
                                    buffer
                                )
                            )

                            totalBytes += lineSize

                            val line = buffer.toString(Charsets.UTF_8).let {
                                if (it.endsWith("\r\n")) it.dropLast(2) else it.dropLast(1)
                            }

                            parser.processLine(line)
                            write(line)
                            write("\n")
                            flush()

                            req.agent.logger.trace {
                                "Streamed proxy line received $lineSize.  Total streamed $totalBytes over ${parser.chunkCount} chunks"
                            }
                        }

                        registerProxyResult(
                            req.agent,
                            LlmProxyResult.Streamed(
                                request = req,
                                usage = LlmUsage(parser.inputTokens, parser.outputTokens),
                                statusCode = response.status,
                                chunkCount = parser.chunkCount
                            )
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        registerProxyResult(
                            req.agent,
                            LlmProxyResult.Exception(
                                request = req,
                                error = e,
                            )
                        )
                    }
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

        if (llmProxyConfig.sendSessionHeaders) {
            header("X-Coral-SessionId", req.agent.session.id)
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
        val body = channel.readRemaining(maxRequestSize.inWholeBytes).readText()

        if (channel.availableForRead > 0 || !channel.isClosedForRead)
            throw LlmProxyException.BufferOverflow("Upstream response exceeded ${maxRequestSize} limit")

        return body
    }

    private fun tryParseJson(body: String): JsonObject? {
        return try {
            json.decodeFromString<JsonObject>(body)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun readBoundedBody(response: HttpResponse): Pair<String, Long> {
        val channel = response.bodyAsChannel()
        val packet = channel.readRemaining(maxResponseSize.inWholeBytes)
        val bytesRead = packet.remaining

        val body = packet.readText()

        if (channel.availableForRead > 0 || !channel.isClosedForRead)
            throw LlmProxyException.BufferOverflow("Upstream response exceeded $maxResponseSize limit")

        return Pair(body, bytesRead)
    }

    private suspend fun registerProxyResult(agent: SessionAgent, result: LlmProxyResult) {
        fun LlmUsage?.report(): String {
            val report =
                listOfNotNull(
                    this?.inputTokens?.let { "$it input tokens" },
                    this?.outputTokens?.let { "$it output tokens" })
                    .joinToString(" and ")

            return if (report.isNotEmpty()) ", using $report" else ""
        }

        when (result) {
            is LlmProxyResult.Buffered -> result.request.logger.info {
                "Proxy request finished in ${result.duration} with status code ${result.statusCode}${result.usage.report()}"
            }

            is LlmProxyResult.Exception -> result.request.logger.error(result.error) {
                "Proxy request failed due to an exception"
            }

            is LlmProxyResult.Streamed -> result.request.logger.info {
                "Streamed proxy request finished in ${result.duration} with status code ${result.statusCode}, with ${result.chunkCount} chunks${result.usage.report()}"
            }
        }

        val (code, usage) = when (result) {
            is LlmProxyResult.Buffered -> Pair(result.statusCode, result.usage)
            is LlmProxyResult.Exception -> return
            is LlmProxyResult.Streamed -> Pair(result.statusCode, result.usage)
        }

        // not emitted for exceptions
        agent.session.events.emit(
            SessionEvent.LlmProxyCall(
                agentName = agent.name,
                modelName = result.request.model.modelName,
                providerRequestName = result.request.proxyRequestName,
                statusCode = code.value,
                usage = usage ?: LlmUsage(),
            )
        )
    }
}

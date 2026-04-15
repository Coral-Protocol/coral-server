package org.coralprotocol.coralserver.llmproxy

import io.ktor.client.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.coralprotocol.coralserver.config.LlmProxyConfig
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.SessionAgent
import kotlin.coroutines.cancellation.CancellationException

private val METHODS_WITH_BODY = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)

private const val MAX_REQUEST_BODY_BYTES = 20 * 1024 * 1024 // 20 MB
private const val MAX_RESPONSE_BODY_BYTES = 80 * 1024 * 1024L // 80 MB
private const val MAX_STREAM_CHARS = 80 * 1024 * 1024L // 80 M chars (~bytes for ASCII SSE)

class LlmProxyException(message: String) : Exception(message)

class LlmProxyService(
    private val config: LlmProxyConfig,
    private val httpClient: HttpClient,
    private val json: Json
) {
    suspend fun proxyRequest(
        agent: SessionAgent,
        providerName: String,
        pathParts: List<String>,
        call: ApplicationCall
    ) {
        val profile = LlmProviderProfile.fromId(providerName) ?: throw RouteException(
            HttpStatusCode.BadRequest,
            "Unknown provider: $providerName"
        )

        val providerConfig = config.providers[providerName]
        val serverKey = providerConfig?.apiKey
        val agentKey = ProxyHeaders.extractAgentKey(call, profile)
        val apiKey = serverKey ?: agentKey ?: throw RouteException(
            HttpStatusCode.Unauthorized,
            "No API key available for provider: $providerName (neither server-configured nor provided by agent)"
        )

        val baseUrl = providerConfig?.baseUrl ?: profile.defaultBaseUrl
        val upstreamUrl = URLBuilder(baseUrl).apply {
            appendEncodedPathSegments(pathParts)
            call.request.queryParameters.entries().forEach { (name, values) ->
                values.forEach { value -> parameters.append(name, value) }
            }
        }.buildString()
        val timeoutMs = ((providerConfig?.timeoutSeconds ?: config.requestTimeoutSeconds) * 1000)
        val hasBody = call.request.httpMethod in METHODS_WITH_BODY
        val requestBody = readRequestBody(hasBody, call)

        val requestJson = if (hasBody) tryParseJson(requestBody) else null
        val isStreaming = requestJson?.get("stream")?.jsonPrimitive?.booleanOrNull == true
        val model = requestJson?.get("model")?.jsonPrimitive?.content

        val finalBody =
            if (isStreaming) profile.strategy.prepareStreamingRequest(requestBody, json, agent.logger) else requestBody

        val req = ProxyRequest(
            agent,
            profile,
            apiKey,
            upstreamUrl,
            timeoutMs,
            finalBody,
            model,
            hasBody,
            System.currentTimeMillis()
        )

        agent.logger.debug {
            "LLM Proxy → $providerName/${
                URLBuilder().appendPathSegments(pathParts).buildString()
            } model=$model streaming=$isStreaming " +
                    "auth=${if (serverKey != null) "server" else "agent"}"
        }

        try {
            if (isStreaming) proxyStreaming(req, call) else proxyBuffered(req, call)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - req.startTime
            emitTelemetry(
                agent,
                LlmCallResult(
                    providerName,
                    model,
                    durationMs = durationMs,
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

    private suspend fun proxyBuffered(req: ProxyRequest, call: ApplicationCall) {
        val response = httpClient.request(req.upstreamUrl) {
            configureProxy(req, call)
        }

        val responseBody = readBoundedBody(response)
        val durationMs = System.currentTimeMillis() - req.startTime

        ProxyHeaders.forwardResponseHeaders(response, call)
        val upstreamContentType = response.contentType() ?: ContentType.Application.Json
        call.respondText(responseBody, upstreamContentType, response.status)

        val usage = req.profile.strategy.extractBufferedTokens(responseBody, json)
        emitTelemetry(
            req.agent,
            LlmCallResult(
                req.profile.providerId, req.model, usage?.inputTokens, usage?.outputTokens, durationMs,
                streaming = false,
                success = response.status.isSuccess(),
                errorKind = if (response.status.isSuccess()) null else classifyHttpError(response.status),
                statusCode = response.status.value
            )
        )
    }

    private suspend fun proxyStreaming(req: ProxyRequest, call: ApplicationCall) {
        httpClient.prepareRequest(req.upstreamUrl) {
            configureProxy(req, call)
            timeout { socketTimeoutMillis = req.timeoutMs }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val durationMs = System.currentTimeMillis() - req.startTime
                val upstreamContentType = response.contentType() ?: ContentType.Application.Json
                call.respondText(errorBody, upstreamContentType, response.status)
                emitTelemetry(
                    req.agent,
                    LlmCallResult(
                        req.profile.providerId, req.model, durationMs = durationMs,
                        streaming = true, success = false,
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
                val parser = req.profile.strategy.createStreamParser(json)
                var totalChars = 0L

                try {
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        totalChars += line.length + 1
                        if (totalChars > MAX_STREAM_CHARS) {
                            val durationMs = System.currentTimeMillis() - req.startTime
                            emitTelemetry(
                                req.agent,
                                LlmCallResult(
                                    req.profile.providerId, req.model, durationMs = durationMs,
                                    streaming = true, success = false,
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

                    if (totalChars <= MAX_STREAM_CHARS) {
                        val durationMs = System.currentTimeMillis() - req.startTime
                        emitTelemetry(
                            req.agent,
                            LlmCallResult(
                                req.profile.providerId, req.model,
                                parser.inputTokens, parser.outputTokens, durationMs,
                                streaming = true, success = true,
                                statusCode = response.status.value, chunkCount = parser.chunkCount
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val durationMs = System.currentTimeMillis() - req.startTime
                    emitTelemetry(
                        req.agent,
                        LlmCallResult(
                            req.profile.providerId, req.model,
                            parser.inputTokens, parser.outputTokens, durationMs,
                            streaming = true, success = false,
                            errorKind = classifyError(e),
                            statusCode = response.status.value
                        )
                    )
                }
            }
        }
    }

    private fun HttpRequestBuilder.configureProxy(req: ProxyRequest, call: ApplicationCall) {
        method = call.request.httpMethod
        timeout { requestTimeoutMillis = req.timeoutMs }
        ProxyHeaders.applyUpstream(this, call, req.profile, req.apiKey)
        if (req.hasBody) {
            contentType(ContentType.Application.Json)
            setBody(req.requestBody)
        }
    }

    private suspend fun readRequestBody(hasBody: Boolean, call: ApplicationCall): String {
        if (!hasBody) return ""
        val declaredLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_REQUEST_BODY_BYTES) {
            throw RouteException(
                HttpStatusCode.PayloadTooLarge,
                "Request body exceeds ${MAX_REQUEST_BODY_BYTES / 1024 / 1024} MB limit"
            )
        }
        val body = call.receiveText()
        if (body.encodeToByteArray().size > MAX_REQUEST_BODY_BYTES) {
            throw RouteException(
                HttpStatusCode.PayloadTooLarge,
                "Request body exceeds ${MAX_REQUEST_BODY_BYTES / 1024 / 1024} MB limit"
            )
        }
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
        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_RESPONSE_BODY_BYTES) {
            throw LlmProxyException("Upstream response exceeds ${MAX_RESPONSE_BODY_BYTES / 1024 / 1024} MB limit")
        }
        val body = response.bodyAsText()
        if (body.encodeToByteArray().size > MAX_RESPONSE_BODY_BYTES) {
            throw LlmProxyException("Upstream response exceeds ${MAX_RESPONSE_BODY_BYTES / 1024 / 1024} MB limit")
        }
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
                agent.logger.debug { "LLM Proxy ← $mode ${result.durationMs}ms$chunks${result.formatTokenInfo()}" }
            } else {
                val mode = if (result.streaming) " (stream)" else ""
                agent.logger.warn { "LLM Proxy ← ${result.statusCode ?: "err"} ${result.durationMs}ms error=${result.errorKind}$mode" }
            }

            agent.accumulateTokens(result.provider, result.model, result.inputTokens, result.outputTokens)
            agent.session.events.emit(
                SessionEvent.LlmProxyCall(
                    agentName = agent.name,
                    provider = result.provider,
                    model = result.model,
                    inputTokens = result.inputTokens,
                    outputTokens = result.outputTokens,
                    durationMs = result.durationMs,
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

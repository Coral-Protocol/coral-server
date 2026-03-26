package org.coralprotocol.coralserver.llm

import io.ktor.client.*
import io.ktor.client.network.sockets.ConnectTimeoutException
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
import kotlinx.serialization.json.longOrNull
import org.coralprotocol.coralserver.config.LlmProxyConfig
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.session.SessionAgent
import kotlin.coroutines.cancellation.CancellationException

private val HOP_BY_HOP_HEADERS = setOf(
    HttpHeaders.Connection,
    HttpHeaders.TransferEncoding,
    HttpHeaders.Upgrade,
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailer",
)

private val STRIP_REQUEST_HEADERS = HOP_BY_HOP_HEADERS + setOf(
    HttpHeaders.Authorization,
    HttpHeaders.Host,
    HttpHeaders.ContentLength,
    "x-api-key",
)

private val STRIP_RESPONSE_HEADERS = HOP_BY_HOP_HEADERS + setOf(
    HttpHeaders.ContentLength,
    HttpHeaders.SetCookie,
)

private val METHODS_WITH_BODY = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)

private const val MAX_REQUEST_BODY_BYTES = 10 * 1024 * 1024 // 10 MB
private const val MAX_RESPONSE_BODY_BYTES = 50 * 1024 * 1024L // 50 MB

class LlmProxyService(
    private val config: LlmProxyConfig,
    private val httpClient: HttpClient,
    private val json: Json,
    private val logger: Logger
) {
    suspend fun proxyRequest(
        agent: SessionAgent,
        providerName: String,
        subPath: String,
        call: ApplicationCall
    ) {
        val profile = LlmProviderProfile.fromId(providerName)
        if (profile == null) {
            call.respond(HttpStatusCode.BadRequest, "Unknown provider: $providerName")
            return
        }

        val providerConfig = config.providers[providerName]
        val apiKey = providerConfig?.apiKey
        if (apiKey == null) {
            call.respond(HttpStatusCode.BadRequest, "No API key configured for provider: $providerName")
            return
        }

        val baseUrl = providerConfig.baseUrl ?: profile.defaultBaseUrl
        val upstreamUrl = "$baseUrl/$subPath"
        val timeoutMs = ((providerConfig.timeoutSeconds ?: config.requestTimeoutSeconds) * 1000)
        val hasBody = call.request.httpMethod in METHODS_WITH_BODY

        if (profile == LlmProviderProfile.MOCK) {
            val requestBody = if (hasBody) call.receiveText() else ""
            val isStreaming = detectStreaming(requestBody)
            handleMock(agent, requestBody, isStreaming, call, System.currentTimeMillis())
            return
        }

        try {
            val requestBody = if (hasBody) call.receiveText() else ""

            if (hasBody && requestBody.length > MAX_REQUEST_BODY_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, "Request body exceeds ${MAX_REQUEST_BODY_BYTES / 1024 / 1024} MB limit")
                return
            }

            val isStreaming = detectStreaming(requestBody)
            val startTime = System.currentTimeMillis()

            logger.debug { "LLM Proxy: ${call.request.httpMethod.value} $providerName/$subPath streaming=$isStreaming agent=${agent.name}" }

            if (isStreaming) {
                proxyStreaming(agent, profile, apiKey, upstreamUrl, timeoutMs, requestBody, hasBody, call, subPath, startTime)
            } else {
                proxyBuffered(agent, profile, apiKey, upstreamUrl, timeoutMs, requestBody, hasBody, call, subPath, startTime)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis()
            logger.error(e) { "LLM Proxy error: $providerName/$subPath after ${durationMs}ms" }
            emitTelemetryEvent(agent, providerName, null, null, null, durationMs, false, false, classifyError(e))
            if (!call.response.isCommitted) {
                call.respond(HttpStatusCode.BadGateway, "Proxy error: ${e.message}")
            }
        }
    }

    private suspend fun proxyBuffered(
        agent: SessionAgent,
        profile: LlmProviderProfile,
        apiKey: String,
        upstreamUrl: String,
        timeoutMs: Long,
        requestBody: String,
        hasBody: Boolean,
        call: ApplicationCall,
        subPath: String,
        startTime: Long
    ) {
        val response = httpClient.request(upstreamUrl) {
            method = call.request.httpMethod
            timeout { requestTimeoutMillis = timeoutMs }
            applyAuth(profile, apiKey)
            applyDefaultHeaders(profile)
            forwardHeaders(call, profile)
            if (hasBody) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
        }

        val contentLength = response.contentLength()
        if (contentLength != null && contentLength > MAX_RESPONSE_BODY_BYTES) {
            val durationMs = System.currentTimeMillis() - startTime
            call.respond(HttpStatusCode.BadGateway, "Upstream response too large: $contentLength bytes")
            emitTelemetryEvent(agent, profile.providerId, extractModel(requestBody), null, null, durationMs, false, false, "response_too_large")
            return
        }

        val responseBody = response.bodyAsText()
        val durationMs = System.currentTimeMillis() - startTime

        val upstreamContentType = response.contentType() ?: ContentType.Application.Json

        for ((name, values) in response.headers.entries()) {
            if (name.lowercase() in STRIP_RESPONSE_HEADERS.map { it.lowercase() }) continue
            values.forEach { call.response.header(name, it) }
        }
        call.respondText(responseBody, upstreamContentType, response.status)

        val errorKind = if (response.status.isSuccess()) null else classifyHttpError(response.status)
        val (inputTokens, outputTokens) = extractTokenUsage(responseBody)
        emitTelemetryEvent(
            agent, profile.providerId, extractModel(requestBody),
            inputTokens, outputTokens, durationMs, false, response.status.isSuccess(), errorKind
        )
    }

    private suspend fun proxyStreaming(
        agent: SessionAgent,
        profile: LlmProviderProfile,
        apiKey: String,
        upstreamUrl: String,
        timeoutMs: Long,
        requestBody: String,
        hasBody: Boolean,
        call: ApplicationCall,
        subPath: String,
        startTime: Long
    ) {
        httpClient.prepareRequest(upstreamUrl) {
            method = call.request.httpMethod
            timeout {
                requestTimeoutMillis = timeoutMs
                socketTimeoutMillis = timeoutMs
            }
            applyAuth(profile, apiKey)
            applyDefaultHeaders(profile)
            forwardHeaders(call, profile)
            if (hasBody) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                val durationMs = System.currentTimeMillis() - startTime
                val upstreamContentType = response.contentType() ?: ContentType.Application.Json
                call.respondText(errorBody, upstreamContentType, response.status)
                emitTelemetryEvent(
                    agent, profile.providerId, extractModel(requestBody),
                    null, null, durationMs, true, false, classifyHttpError(response.status)
                )
                return@execute
            }

            call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.response.header(HttpHeaders.Connection, "keep-alive")
            call.response.header("X-Accel-Buffering", "no")

            call.respondTextWriter {
                val channel = response.bodyAsChannel()

                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    write(line)
                    write("\n")
                    flush()
                }

                val durationMs = System.currentTimeMillis() - startTime
                emitTelemetryEvent(
                    agent, profile.providerId, extractModel(requestBody),
                    null, null, durationMs, true, true, null
                )
            }
        }
    }

    private suspend fun handleMock(
        agent: SessionAgent,
        requestBody: String,
        isStreaming: Boolean,
        call: ApplicationCall,
        startTime: Long
    ) {
        val model = extractModel(requestBody) ?: "mock-model"
        call.response.header("x-coral-mock", "true")

        if (isStreaming) {
            val streamBody = buildString {
                appendLine("data: ${mockStreamChunk(model, "Hello", null)}")
                appendLine()
                appendLine("data: ${mockStreamChunk(model, " from", null)}")
                appendLine()
                appendLine("data: ${mockStreamChunk(model, " mock", "stop", 10, 3)}")
                appendLine()
                appendLine("data: [DONE]")
                appendLine()
            }
            call.respondText(streamBody, ContentType.Text.EventStream, HttpStatusCode.OK)
        } else {
            call.respondText(
                mockBufferedResponse(model, "Hello from mock", 10, 3),
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        }

        val durationMs = System.currentTimeMillis() - startTime
        emitTelemetryEvent(agent, "mock", model, 10, 3, durationMs, isStreaming, true, null)
    }

    private fun mockBufferedResponse(model: String, content: String, promptTokens: Long, completionTokens: Long): String =
        """{"id":"mock-1","object":"chat.completion","model":"$model","choices":[{"index":0,"message":{"role":"assistant","content":"$content"},"finish_reason":"stop"}],"usage":{"prompt_tokens":$promptTokens,"completion_tokens":$completionTokens,"total_tokens":${promptTokens + completionTokens}}}"""

    private fun mockStreamChunk(model: String, content: String, finishReason: String?, promptTokens: Long? = null, completionTokens: Long? = null): String {
        val usagePart = if (promptTokens != null) ""","usage":{"prompt_tokens":$promptTokens,"completion_tokens":$completionTokens,"total_tokens":${promptTokens + (completionTokens ?: 0)}}""" else ""
        val finishPart = if (finishReason != null) """"$finishReason"""" else "null"
        return """{"id":"mock-1","object":"chat.completion.chunk","model":"$model","choices":[{"index":0,"delta":{"content":"$content"},"finish_reason":$finishPart}]$usagePart}"""
    }

    private fun HttpRequestBuilder.applyAuth(profile: LlmProviderProfile, apiKey: String) {
        when (profile.authStyle) {
            is AuthStyle.Bearer -> header(HttpHeaders.Authorization, "Bearer $apiKey")
            is AuthStyle.Custom -> header(profile.authStyle.headerName, apiKey)
        }
    }

    private fun HttpRequestBuilder.applyDefaultHeaders(profile: LlmProviderProfile) {
        profile.defaultHeaders.forEach { (name, value) ->
            header(name, value)
        }
    }

    private fun HttpRequestBuilder.forwardHeaders(call: ApplicationCall, profile: LlmProviderProfile) {
        val stripLower = STRIP_REQUEST_HEADERS.map { it.lowercase() }.toSet()
        val defaultHeadersLower = profile.defaultHeaders.keys.map { it.lowercase() }.toSet()

        for ((name, values) in call.request.headers.entries()) {
            val nameLower = name.lowercase()
            if (nameLower in stripLower) continue
            if (nameLower in defaultHeadersLower) continue
            values.forEach { header(name, it) }
        }
    }

    private fun detectStreaming(requestBody: String): Boolean {
        return try {
            val obj = json.decodeFromString<JsonObject>(requestBody)
            obj["stream"]?.jsonPrimitive?.booleanOrNull == true
        } catch (_: Exception) {
            false
        }
    }

    private fun extractModel(requestBody: String): String? {
        return try {
            val obj = json.decodeFromString<JsonObject>(requestBody)
            obj["model"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTokenUsage(responseBody: String): Pair<Long?, Long?> {
        return try {
            val obj = json.decodeFromString<JsonObject>(responseBody)
            val usage = obj["usage"] as? JsonObject ?: return null to null
            val input = usage["prompt_tokens"]?.jsonPrimitive?.longOrNull
                ?: usage["input_tokens"]?.jsonPrimitive?.longOrNull
            val output = usage["completion_tokens"]?.jsonPrimitive?.longOrNull
                ?: usage["output_tokens"]?.jsonPrimitive?.longOrNull
            input to output
        } catch (_: Exception) {
            null to null
        }
    }

    private fun classifyHttpError(status: HttpStatusCode): String = when {
        status.value == 429 -> "rate_limited"
        status.value in 401..403 -> "credentials"
        status.value in 500..599 -> "upstream_health"
        status.value in 400..499 -> "request_error"
        else -> "unknown"
    }

    private fun classifyError(e: Exception): String = when {
        e is ConnectTimeoutException -> "connectivity"
        e is HttpRequestTimeoutException -> "connectivity"
        e.message?.contains("timeout", ignoreCase = true) == true -> "connectivity"
        else -> "unknown"
    }

    private suspend fun emitTelemetryEvent(
        agent: SessionAgent,
        provider: String,
        model: String?,
        inputTokens: Long?,
        outputTokens: Long?,
        durationMs: Long,
        streaming: Boolean,
        success: Boolean,
        errorKind: String?
    ) {
        try {
            agent.session.events.emit(
                SessionEvent.LlmProxyCall(
                    agentName = agent.name,
                    provider = provider,
                    model = model,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    durationMs = durationMs,
                    streaming = streaming,
                    success = success,
                    errorKind = errorKind
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to emit LLM proxy telemetry event" }
        }
    }
}

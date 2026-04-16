package org.coralprotocol.coralserver.llmproxy

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header

object ProxyHeaders {
    private val HOP_BY_HOP = setOf(
        HttpHeaders.Connection,
        HttpHeaders.TransferEncoding,
        HttpHeaders.Upgrade,
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailer",
    )

    private val STRIP_REQUEST = (HOP_BY_HOP + setOf(
        HttpHeaders.Authorization,
        HttpHeaders.Host,
        HttpHeaders.ContentLength,
        HttpHeaders.ContentType,
        HttpHeaders.AcceptEncoding,
        HttpHeaders.Cookie,
        "x-api-key",
    )).map { it.lowercase() }.toSet()

    private val STRIP_RESPONSE = (HOP_BY_HOP + setOf(
        HttpHeaders.ContentLength,
        HttpHeaders.ContentEncoding,
        HttpHeaders.SetCookie,
    )).map { it.lowercase() }.toSet()

    fun applyUpstream(builder: HttpRequestBuilder, call: ApplicationCall, profile: LlmProviderProfile, apiKey: String) {
        when (profile.authStyle) {
            is AuthStyle.Bearer -> builder.header(HttpHeaders.Authorization, "Bearer $apiKey")
            is AuthStyle.Custom -> builder.header(profile.authStyle.headerName, apiKey)
        }

        profile.defaultHeaders.forEach { (name, value) -> builder.header(name, value) }

        val defaultLower = profile.defaultHeaders.keys.map { it.lowercase() }.toSet()
        for ((name, values) in call.request.headers.entries()) {
            val lower = name.lowercase()
            if (lower in STRIP_REQUEST || lower in defaultLower) continue
            values.forEach { builder.header(name, it) }
        }
    }

    fun forwardResponseHeaders(from: HttpResponse, call: ApplicationCall) {
        for ((name, values) in from.headers.entries()) {
            if (name.lowercase() in STRIP_RESPONSE) continue
            values.forEach { call.response.header(name, it) }
        }
    }

    fun extractAgentKey(call: ApplicationCall, profile: LlmProviderProfile): String? {
        return when (profile.authStyle) {
            is AuthStyle.Bearer -> {
                val authHeader = call.request.headers[HttpHeaders.Authorization] ?: return null
                if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
                    authHeader.substring(7).trim().ifEmpty { null }
                } else null
            }
            is AuthStyle.Custom -> {
                call.request.headers[profile.authStyle.headerName]?.trim()?.ifEmpty { null }
            }
        }
    }
}

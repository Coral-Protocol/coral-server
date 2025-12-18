@file:OptIn(ExperimentalStdlibApi::class)

package org.coralprotocol.coralserver.util

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.server.apiJsonConfig
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val SIGNATURE_ALGORITHM = "HmacSHA256"

inline fun <reified T> HttpRequestBuilder.addJsonBodyWithSignature(
    secret: String,
    body: T,
    header: String = "X-Signature",
) {
    val json = apiJsonConfig.encodeToString(body)

    val mac = Mac.getInstance(SIGNATURE_ALGORITHM)
    val secretKey = SecretKeySpec(secret.toByteArray(), SIGNATURE_ALGORITHM)
    mac.init(secretKey)

    val signature = mac.doFinal(json.toByteArray())

    header(header, signature.toHexString(HexFormat.Default))
    contentType(ContentType.Application.Json)
    setBody(json)
}

suspend inline fun <reified T> RoutingContext.signatureVerifiedBody(
    secret: String,
    header: String = "X-Signature"
): T {
    val json = call.receiveText()
    val mac = Mac.getInstance(SIGNATURE_ALGORITHM)
    val secretKey = SecretKeySpec(secret.toByteArray(), SIGNATURE_ALGORITHM)
    mac.init(secretKey)

    val signature = call.request.header(header)
        ?: throw RouteException(HttpStatusCode.Unauthorized)

    val computedSignature = mac.doFinal(json.toByteArray())
    if (!MessageDigest.isEqual(signature.hexToByteArray(HexFormat.Default), computedSignature))
        throw RouteException(HttpStatusCode.Unauthorized)

    return apiJsonConfig.decodeFromString(json)
}
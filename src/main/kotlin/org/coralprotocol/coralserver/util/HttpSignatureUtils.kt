@file:OptIn(ExperimentalStdlibApi::class)

package org.coralprotocol.coralserver.util

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.routes.RouteException
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val SIGNATURE_ALGORITHM = "HmacSHA256"
const val CORAL_SIGNATURE_HEADER = "X-Coral-Signature"

inline fun <reified T> HttpRequestBuilder.addJsonBodyWithSignature(
    json: Json,
    secret: String,
    body: T,
    header: String = CORAL_SIGNATURE_HEADER,
) {
    val json = json.encodeToString(body)

    val mac = Mac.getInstance(SIGNATURE_ALGORITHM)
    val secretKey = SecretKeySpec(secret.toByteArray(), SIGNATURE_ALGORITHM)
    mac.init(secretKey)

    val signature = mac.doFinal(json.toByteArray())

    header(header, signature.toHexString(HexFormat.Default))
    contentType(ContentType.Application.Json)
    setBody(json)
}

suspend inline fun <reified T> RoutingContext.signatureVerifiedBody(
    json: Json,
    secret: String,
    header: String = CORAL_SIGNATURE_HEADER
): T {
    val jsonObj = call.receiveText()
    val mac = Mac.getInstance(SIGNATURE_ALGORITHM)
    val secretKey = SecretKeySpec(secret.toByteArray(), SIGNATURE_ALGORITHM)
    mac.init(secretKey)

    val signature = call.request.header(header)
        ?: throw RouteException(HttpStatusCode.Unauthorized)

    val computedSignature = mac.doFinal(jsonObj.toByteArray())
    if (!MessageDigest.isEqual(signature.hexToByteArray(HexFormat.Default), computedSignature))
        throw RouteException(HttpStatusCode.Unauthorized)

    return json.decodeFromString(jsonObj)
}
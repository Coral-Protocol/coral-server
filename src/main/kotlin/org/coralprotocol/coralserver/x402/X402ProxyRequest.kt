package org.coralprotocol.coralserver.x402

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@Description("A proxied request to a service that potentially requests x402 payment")
data class X402ProxyRequest(
    @Description("The full endpoint of the target API")
    val endpoint: String,

    @Description("The API method, e.g. POST, GET, DELETE, etc")
    val method: String,

    @Description("The body of the request")
    val body: JsonObject,

    // todo: headers
)

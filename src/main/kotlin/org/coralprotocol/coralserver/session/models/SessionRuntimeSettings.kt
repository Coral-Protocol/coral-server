package org.coralprotocol.coralserver.session.models

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class SessionRuntimeSettings(
    @Description("If specified, the session will never live longer than this many milliseconds.")
    val ttl: Long? = null,

    @Description("If true, when a TTL is specified, even after the session naturally closes, the session will remain in memory until the TTL expires")
    val holdForTtl: Boolean = false
)

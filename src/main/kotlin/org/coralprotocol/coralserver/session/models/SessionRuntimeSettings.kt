@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session.models

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator


@Serializable
@JsonClassDiscriminator("mode")
sealed interface SessionPersistenceMode {
    @Description("No persistence")
    @Serializable
    @SerialName("none")
    object None : SessionPersistenceMode

    @Description("Session will exist for at least the specified time (in milliseconds).  This time includes the run time of the session.")
    @Serializable
    @SerialName("minimum_time")
    data class MinimumTime(
        val time: Long
    ) : SessionPersistenceMode

    @Description("Session will exist for at least the specified time (in milliseconds) after the session exits.  This time does not include the run time of the session.")
    @Serializable
    @SerialName("hold_after_exit")
    data class HoldAfterExit(
        val duration: Long
    ) : SessionPersistenceMode
}

@Serializable
@Description("The webhook that is called when this session ends.  The model sent is a SessionEndState")
data class SessionEndWebhook(val url: String)

@Serializable
data class SessionWebhooks(
    val sessionEnd: SessionEndWebhook? = null
)

@Serializable
data class SessionRuntimeSettings(
    @Description("If specified, the session will never live longer than this many milliseconds.")
    val ttl: Long? = null,

    @Description("Persistence mode for the session. Default is \"none\" meaning the session will be deleted as soon as it exits")
    val persistenceMode: SessionPersistenceMode = SessionPersistenceMode.None,

    @Description("Webhooks executed for this session")
    val webhooks: SessionWebhooks = SessionWebhooks()
)

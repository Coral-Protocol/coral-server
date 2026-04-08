@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime.prototype

import dev.eav.tomlkt.TomlClassDiscriminator
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

@Serializable
@JsonClassDiscriminator("type")
@TomlClassDiscriminator("type")
sealed interface PrototypeApiUrl {
    fun resolve(executionContext: SessionAgentExecutionContext): String

    @Serializable
    @SerialName("proxy")
    object Proxy : PrototypeApiUrl {
        override fun resolve(executionContext: SessionAgentExecutionContext): String {
            TODO("Not yet implemented")
        }
    }

    @Serializable
    @SerialName("custom")
    data class Custom(
        val value: String
    ) : PrototypeApiUrl {
        override fun resolve(executionContext: SessionAgentExecutionContext) = value
    }
}
@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime.prototype

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.exceptions.PrototypeRuntimeException
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.value
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

@Serializable
@JsonClassDiscriminator("type")
sealed interface PrototypeString {
    fun resolve(executionContext: SessionAgentExecutionContext): String

    @Serializable
    @SerialName("inline")
    data class Inline(val value: String) : PrototypeString {
        override fun resolve(executionContext: SessionAgentExecutionContext): String = value
    }

    @Serializable
    @SerialName("option")
    data class Option(val name: String) : PrototypeString {
        override fun resolve(executionContext: SessionAgentExecutionContext): String {
            val option = executionContext.agent.graphAgent.options[name]
                ?: throw PrototypeRuntimeException.BadOption("option \"$name\" wasn't found")

            val optionValue = option.value()
            if (optionValue !is AgentOptionValue.String)
                throw PrototypeRuntimeException.BadOption("option \"$name\" must have type=\"string\"")

            return optionValue.value
        }
    }
}
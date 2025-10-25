package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.exceptions.AgentOptionValidationException
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.compareTypeWithValue
import org.coralprotocol.coralserver.agent.registry.option.requireValue
import org.coralprotocol.coralserver.agent.registry.option.withValue
import org.coralprotocol.coralserver.agent.runtime.RuntimeId

typealias UnresolvedAgentExportSettingsMap = Map<RuntimeId, UnresolvedAgentExportSettings>

@Serializable
data class UnresolvedAgentExportSettings(
    val quantity: UInt,
    val pricing: RegistryAgentExportPricing,
    val options: Map<String, AgentOptionValue> = mapOf()
) {
    fun resolve(runtimeId: RuntimeId, agent: RegistryAgent): AgentExportSettings {
        if (quantity == 0u) {
            throw RegistryException("Cannot export 0 \"${agent.info.identifier}\" agents")
        }

        if (agent.runtimes.getById(runtimeId) == null) {
            throw RegistryException("Runtime \"$runtimeId\" is not defined for agent \"${agent.info.identifier}\"")
        }

        for ((optionName, optionValue) in options) {
            val option = agent.options[optionName]
                ?: throw RegistryException("Cannot export unknown option \"$optionName\" for agent \"${agent.info.identifier}\"")

            if (!option.compareTypeWithValue(optionValue)) {
                val valueType = optionValue.javaClass.name
                val optionType = option.javaClass.name
                throw RegistryException("Wrong value type \"$valueType\" given for option \"$optionName\" in \"${agent.info.identifier}\".  Expected type \"$optionType\"")
            }

            try {
                option.withValue(optionValue).requireValue()
            }
            catch (e: AgentOptionValidationException) {
                throw RegistryException("Value given for option \"$optionName\" in \"${agent.info.identifier}\" is invalid: ${e.message}")
            }
        }

        return AgentExportSettings(
            quantity = quantity,
            pricing = pricing,
            options = options
        )
    }
}

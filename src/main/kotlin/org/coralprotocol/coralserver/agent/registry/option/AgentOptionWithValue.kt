package org.coralprotocol.coralserver.agent.registry.option

import org.coralprotocol.coralserver.config.DockerConfig
import org.coralprotocol.coralserver.session.SessionAgentDisposableResource
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

typealias AnyAgentOptionWithValue = AgentOptionWithValue<*, *, *>

class AgentOptionWithValue<OptionType, ValueType, BackingType>(
    val option: OptionType,
    val value: ValueType
) where OptionType : PolymorphicAgentOption<ValueType, BackingType>,
        ValueType : PolymorphicAgentOptionValue<BackingType> {
    fun validateValue() =
        option.validateValue(value)

    fun displayValue() =
        option.displayValue(value)

    fun asEnvVarValue() = when (option) {
        is PolymorphicAgentOption.Blob -> value.asEnvVarValue(true)
        is PolymorphicAgentOption.BlobList -> value.asEnvVarValue(true)
        is PolymorphicAgentOption.String -> value.asEnvVarValue(option.base64)
        is PolymorphicAgentOption.StringList -> value.asEnvVarValue(option.base64)
        else -> value.asEnvVarValue()
    }

    /**
     * Writes the value of this option to file(s) using the values [PolymorphicAgentOptionValue.toFileSystemValue] function.  Note that
     * the return type is always a list.  For single value type options, a list with 1 value will be returned.  For list-type
     * options, a list of temporary files; one for every value in the option, will be returned.
     *
     * The temporary files are represented by the [SessionAgentDisposableResource.TemporaryFile] type, which is only
     * designed for use in [SessionAgentExecutionContext]
     */
    fun asFileSystemValue(dockerConfig: DockerConfig): List<SessionAgentDisposableResource.TemporaryFile> {
        return value.toFileSystemValue().map {
            SessionAgentDisposableResource.TemporaryFile(it, dockerConfig)
        }
    }
}
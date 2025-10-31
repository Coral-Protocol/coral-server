package org.coralprotocol.coralserver.agent.registry.option

import kotlinx.serialization.SerialName

enum class AgentOptionTransport {
    /**
     * The option will be sent to the agent via an environment variable.  An environment variable will be set where the
     * value was the value specified for the option.  Note that environment variables have OS-dependent constraints.
     *
     * If the value is likely to be large (>1 kb, for example), it is best to use the [FILE_SYSTEM] transport.
     */
    @SerialName("env")
    ENVIRONMENT_VARIABLE,

    /**
     * The file system transport will write a temporary file to represent the value of this option and will pass the
     * path to the file to the agent using an environment variable.
     *
     * The temporary file will be mounted into the agent container if Docker is used.
     */
    @SerialName("fs")
    FILE_SYSTEM,
}
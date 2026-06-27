package org.coralprotocol.coralserver.agent.registry

open class RegistryException(override val message: String, cause: Throwable? = null) : Exception(message, cause) {
    class RegistrySourceNotFoundException(message: String, cause: Throwable? = null) : RegistryException(message, cause)
    class AgentNotFoundException(message: String, cause: Throwable? = null) : RegistryException(message, cause)
}
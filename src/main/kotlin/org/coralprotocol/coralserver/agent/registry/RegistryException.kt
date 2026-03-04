package org.coralprotocol.coralserver.agent.registry

open class RegistryException(override val message: String) : Exception(message) {
    class RegistrySourceNotFoundException(message: String) : RegistryException(message)
    class AgentNotFoundException(message: String) : RegistryException(message)
}
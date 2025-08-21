package org.coralprotocol.coralserver.agent.registry

data class RegistryException(override val message: String?) : Exception(message)
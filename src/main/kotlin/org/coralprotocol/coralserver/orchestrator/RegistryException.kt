package org.coralprotocol.coralserver.orchestrator

data class RegistryException(override val message: String?) : Exception(message)
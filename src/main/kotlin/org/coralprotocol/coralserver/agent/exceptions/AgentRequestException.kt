package org.coralprotocol.coralserver.agent.exceptions

open class AgentRequestException(message: String): Exception(message) {
    class SessionNotFundedException(message: String) : AgentRequestException(message)
    class NoServer(message: String) : AgentRequestException(message)
}

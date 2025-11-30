package org.coralprotocol.coralserver.session

open class SessionException(message: String): Exception(message) {
    class MissingAgentException(message: String) : SessionException(message)
    class MissingThreadException(message: String) : SessionException(message)
    class IllegalThreadMentionException(message: String) : SessionException(message)
    class ThreadClosedException(message: String) : SessionException(message)
    class InvalidAgentSecret(message: String) : SessionException(message)
    class InvalidNamespace(message: String) : SessionException(message)
    class AlreadyLaunchedException(message: String) : SessionException(message)
    class NotLaunchedException(message: String) : SessionException(message)
}
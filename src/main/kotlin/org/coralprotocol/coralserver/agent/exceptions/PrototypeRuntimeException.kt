package org.coralprotocol.coralserver.agent.exceptions

open class PrototypeRuntimeException(message: String): Exception(message) {
    class BadOption(message: String) : PrototypeRuntimeException(message)
    class BadModel(message: String) : PrototypeRuntimeException(message)
}

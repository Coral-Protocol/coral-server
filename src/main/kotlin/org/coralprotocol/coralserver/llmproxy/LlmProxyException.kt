package org.coralprotocol.coralserver.llmproxy

sealed class LlmProxyException(message: String) : Exception(message) {
    class ProxyRequestResolutionError(message: String) : LlmProxyException(message)
    class BufferOverflow(message: String) : LlmProxyException(message)
}
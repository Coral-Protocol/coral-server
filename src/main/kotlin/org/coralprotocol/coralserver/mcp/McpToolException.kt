package org.coralprotocol.coralserver.mcp

import org.coralprotocol.coralserver.session.SessionException

class McpToolException(
    override val message: String,
): Exception(message)

fun SessionException.toMcpToolException() = McpToolException(message)
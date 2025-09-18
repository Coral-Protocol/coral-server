package org.coralprotocol.coralserver.agent.runtime

import io.ktor.http.*

fun getCoralSystemEnvs(
    params: RuntimeParams,
    apiUrl: Url,
    mcpUrl: Url,
    orchestrationRuntime: String
): Map<String, String> {
    return listOfNotNull(
        "CORAL_CONNECTION_URL" to mcpUrl.toString(),
        "CORAL_AGENT_ID" to params.agentName,
        "CORAL_ORCHESTRATION_RUNTIME" to orchestrationRuntime,
        "CORAL_SESSION_ID" to when (params) {
            is RuntimeParams.Local -> params.session.id
            is RuntimeParams.Remote -> params.session.id
        },
        "CORAL_SEND_CLAIMS" to when (params) {
            is RuntimeParams.Local -> "0"
            is RuntimeParams.Remote -> "1"
        },
        "CORAL_API_URL" to apiUrl.toString(),
        "CORAL_SSE_URL" to with(mcpUrl) { //TODO: Remove this as it should be identical to CORAL_CONNECTION_URL
            "${protocol.name}://$host:$port$encodedPath"
        },
        params.systemPrompt?.let { "CORAL_PROMPT_SYSTEM" to it }
    ).toMap()
}
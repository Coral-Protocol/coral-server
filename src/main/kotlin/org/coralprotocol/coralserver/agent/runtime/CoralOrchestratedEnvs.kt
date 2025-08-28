package org.coralprotocol.coralserver.agent.runtime

import io.ktor.http.Url

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
        "CORAL_SESSION_ID" to params.sessionId,
        "CORAL_API_URL" to apiUrl.toString(),
        "CORAL_SSE_URL" to with(mcpUrl) {
            "${protocol}://$host:$port$encodedPath"
        },
        params.systemPrompt?.let { "CORAL_PROMPT_SYSTEM" to it }
    ).toMap()
}
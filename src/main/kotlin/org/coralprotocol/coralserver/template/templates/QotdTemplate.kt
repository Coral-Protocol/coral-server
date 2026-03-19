package org.coralprotocol.coralserver.template.templates

import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.session.SessionRequest
import org.coralprotocol.coralserver.template.*

object QotdTemplate : SessionTemplate {

    private const val AGENT_NAME = "coral-base-agent"
    private const val AGENT_VERSION = "1.0.0"

    override val info = SessionTemplateInfo(
        slug = "qotd",
        name = "Quote of the Day",
        description = "Three AI agents collaborate to select a theme, research matching quotes, and present the best one. A simple demonstration of multi-agent collaboration on Coral.",
        category = "demo",
        agentCount = 3,
        estimatedDuration = "~30 seconds",
        estimatedCost = "~\$0.01 (gpt-5-mini)",
        parameters = COMMON_LLM_PARAMETERS,
    )

    // Prompts are intentionally minimal — coral://instruction provides all tool usage
    // rules, mention rules, threading rules, and waiting patterns automatically.

    private val themeCuratorPrompt = """
        You are "theme-curator" in a Quote of the Day system.

        Pick an interesting, specific theme (e.g. "the courage to begin again", "finding humor in failure"). Then:
        1. Create a thread called "qotd" with participants: theme-curator, quote-researcher, quote-presenter
        2. Send your theme on that thread, mentioning quote-researcher
        3. Wait for the final presentation
    """.trimIndent()

    private val quoteResearcherPrompt = """
        You are "quote-researcher" in a Quote of the Day system.

        You must wait for theme-curator to mention you before doing anything.
        Do NOT create threads. Do NOT send messages until you receive a mention.
        Your first action must be to wait for a mention.

        After you receive a mention with a theme:
        1. Find 3 real, well-attributed quotes from notable figures matching the theme
        2. Send them on the SAME thread you were mentioned in, mentioning quote-presenter
    """.trimIndent()

    private val quotePresenterPrompt = """
        You are "quote-presenter" in a Quote of the Day system.

        You must wait for quote-researcher to mention you before doing anything.
        Do NOT create threads. Do NOT send messages until you receive a mention.
        Your first action must be to wait for a mention.

        After you receive a mention with candidate quotes:
        1. Pick the best quote and present it beautifully with author, why it matters, and a reflection prompt
        2. Send it on the SAME thread you were mentioned in, mentioning theme-curator
        3. Call the coral_close_session tool to end the entire session. This is critical — use coral_close_session, NOT coral_close_thread.
    """.trimIndent()

    private fun agentOptions(parameters: Map<String, String>) =
        buildCommonAgentOptions(parameters, maxIterations = "3")

    override fun buildSessionRequest(
        parameters: Map<String, String>,
        namespace: String,
        registrySource: AgentRegistrySourceIdentifier,
        runtime: RuntimeId,
    ): SessionRequest {

        val agents = listOf(
            sessionTemplateAgent(
                agentName = AGENT_NAME,
                agentVersion = AGENT_VERSION,
                registrySource = registrySource,
                instanceName = "theme-curator",
                description = "Selects a compelling daily theme for the quote",
                systemPrompt = themeCuratorPrompt,
                options = agentOptions(parameters),
                runtime = runtime,
            ),
            sessionTemplateAgent(
                agentName = AGENT_NAME,
                agentVersion = AGENT_VERSION,
                registrySource = registrySource,
                instanceName = "quote-researcher",
                description = "Finds and proposes quotes matching the theme",
                systemPrompt = quoteResearcherPrompt,
                options = agentOptions(parameters),
                runtime = runtime,
            ),
            sessionTemplateAgent(
                agentName = AGENT_NAME,
                agentVersion = AGENT_VERSION,
                registrySource = registrySource,
                instanceName = "quote-presenter",
                description = "Selects the best quote, formats the final presentation, and closes the session",
                systemPrompt = quotePresenterPrompt,
                options = agentOptions(parameters),
                runtime = runtime,
                plugins = setOf(GraphAgentPlugin.CloseSessionTool),
            ),
        )

        return sessionTemplateRequest(
            agents = agents,
            groups = setOf(setOf("theme-curator", "quote-researcher", "quote-presenter")),
            namespace = namespace,
            ttlMs = 300_000,
            annotations = mapOf(
                "template" to "qotd",
                "templateVersion" to "1.0.0",
            ),
        )
    }
}

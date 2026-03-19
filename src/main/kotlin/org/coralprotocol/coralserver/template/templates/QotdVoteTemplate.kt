package org.coralprotocol.coralserver.template.templates

import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.session.SessionRequest
import org.coralprotocol.coralserver.template.*

object QotdVoteTemplate : SessionTemplate {

    private const val AGENT_NAME = "coral-base-agent"
    private const val AGENT_VERSION = "1.0.0"

    override val info = SessionTemplateInfo(
        slug = "qotd-vote",
        name = "Quote of the Day (Voting)",
        description = "Four AI agents collaborate with a voting round to select the best quote. Demonstrates multi-party discussion, vote collection, and coordinated decision-making on Coral.",
        category = "demo",
        agentCount = 4,
        estimatedDuration = "~60 seconds",
        estimatedCost = "~\$0.03 (gpt-5-mini)",
        parameters = COMMON_LLM_PARAMETERS,
    )

    private val themePickerPrompt = """
        You are "theme-picker" in a Quote of the Day voting system with 4 agents:
        theme-picker, quote-finder, vote-coordinator, presenter.

        PART 1 — Pick a theme:
        1. Create a thread called "qotd-vote" with participants: theme-picker, quote-finder, vote-coordinator, presenter
        2. Pick an interesting, specific theme (e.g. "the courage to begin again", "finding humor in failure")
        3. Send your theme on that thread, mentioning quote-finder
        4. Wait for a mention (vote-coordinator will ask you to vote)

        PART 2 — Vote (each time vote-coordinator asks):
        1. Pick your favorite quote (by number) and explain briefly why in 1-2 sentences
        2. Send your vote on the same thread, mentioning vote-coordinator
        3. Wait for the next mention
    """.trimIndent()

    private val quoteFinderPrompt = """
        You are "quote-finder" in a Quote of the Day voting system.

        You must wait for a mention before doing anything.
        Do NOT create threads. Do NOT send messages until you receive a mention.
        Your first action must be to wait for a mention.

        FIRST TIME — when theme-picker mentions you with a theme:
        1. Find 5 real, well-attributed quotes from notable figures matching the theme
        2. Number them 1 through 5, with author and brief context for each
        3. Send them on the SAME thread, mentioning vote-coordinator

        VOTING — when vote-coordinator asks you to vote:
        1. Pick your favorite quote (by number) and explain briefly why in 1-2 sentences
        2. Send your vote on the same thread, mentioning vote-coordinator
        3. Wait for the next mention

        IF ASKED TO REPLACE A QUOTE — when vote-coordinator asks you to swap out a specific quote:
        1. Find a new replacement quote matching the theme (different from all previous ones)
        2. Send the updated list on the same thread, mentioning vote-coordinator
    """.trimIndent()

    private val voteCoordinatorPrompt = """
        You are "vote-coordinator" in a Quote of the Day voting system.

        You must wait for quote-finder to mention you before doing anything.
        Do NOT create threads. Do NOT send messages until you receive a mention.
        Your first action must be to wait for a mention.

        After you receive numbered quotes from quote-finder:
        1. Re-post the 5 quotes as a numbered list
        2. Ask ALL other agents to vote: mention theme-picker, quote-finder, AND presenter
        3. Wait for 3 votes (call coral_wait_for_mention three times)
        4. Tally the votes:
           - If a quote has a majority (2+ votes), that's the winner
           - If NO majority (all different votes): ask quote-finder to replace the least-voted quote, then run another vote round (go back to step 1 with updated quotes)
        5. Once there's a winner, announce it and mention presenter to present it

        You also cast your own vote when tallying — you are the 4th voter and tiebreaker.
        Maximum 2 voting rounds. If still no majority after round 2, pick the quote with the most votes yourself.
    """.trimIndent()

    private val presenterPrompt = """
        You are "presenter" in a Quote of the Day voting system.

        You must wait for a mention before doing anything.
        Do NOT create threads. Do NOT send messages until you receive a mention.
        Your first action must be to wait for a mention.

        VOTING — when vote-coordinator asks you to vote:
        1. Pick your favorite quote (by number) and explain briefly why in 1-2 sentences
        2. Send your vote on the same thread, mentioning vote-coordinator
        3. Wait for the next mention

        PRESENTING — when vote-coordinator announces the winning quote:
        1. Present the winning quote beautifully: the quote, author, why it matters, and a reflection prompt
        2. Send the presentation on the same thread, mentioning theme-picker
        3. Call the coral_close_session tool to end the session. This is critical — use coral_close_session, NOT coral_close_thread.
    """.trimIndent()

    private fun agentOptions(parameters: Map<String, String>) =
        buildCommonAgentOptions(parameters, maxIterations = "10")

    override fun buildSessionRequest(
        parameters: Map<String, String>,
        namespace: String,
        registrySource: AgentRegistrySourceIdentifier,
        runtime: RuntimeId,
    ): SessionRequest {

        val allAgents = setOf("theme-picker", "quote-finder", "vote-coordinator", "presenter")

        val agents = listOf(
            sessionTemplateAgent(
                agentName = AGENT_NAME,
                agentVersion = AGENT_VERSION,
                registrySource = registrySource,
                instanceName = "theme-picker",
                description = "Picks a theme and votes on quotes",
                systemPrompt = themePickerPrompt,
                options = agentOptions(parameters),
                runtime = runtime,
            ),
            sessionTemplateAgent(
                agentName = AGENT_NAME,
                agentVersion = AGENT_VERSION,
                registrySource = registrySource,
                instanceName = "quote-finder",
                description = "Finds 5 candidate quotes matching the theme",
                systemPrompt = quoteFinderPrompt,
                options = agentOptions(parameters),
                runtime = runtime,
            ),
            sessionTemplateAgent(
                agentName = AGENT_NAME,
                agentVersion = AGENT_VERSION,
                registrySource = registrySource,
                instanceName = "vote-coordinator",
                description = "Collects votes from agents and tallies the result",
                systemPrompt = voteCoordinatorPrompt,
                options = agentOptions(parameters),
                runtime = runtime,
            ),
            sessionTemplateAgent(
                agentName = AGENT_NAME,
                agentVersion = AGENT_VERSION,
                registrySource = registrySource,
                instanceName = "presenter",
                description = "Votes on quotes and presents the winning quote",
                systemPrompt = presenterPrompt,
                options = agentOptions(parameters),
                runtime = runtime,
                plugins = setOf(GraphAgentPlugin.CloseSessionTool),
            ),
        )

        return sessionTemplateRequest(
            agents = agents,
            groups = setOf(allAgents),
            namespace = namespace,
            ttlMs = 300_000,
            annotations = mapOf(
                "template" to "qotd-vote",
                "templateVersion" to "1.0.0",
            ),
        )
    }
}

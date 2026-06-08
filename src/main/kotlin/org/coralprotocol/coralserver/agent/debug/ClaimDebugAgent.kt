package org.coralprotocol.coralserver.agent.debug

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.payment.*
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.UnresolvedAgentExportSettings
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.buildFullOption
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.routes.api.v1.Rpc
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.SessionAgent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ClaimDebugAgent(client: HttpClient) : DebugAgent(client) {
    override val companion: DebugAgentIdHolder
        get() = Companion

    companion object : DebugAgentIdHolder {
        override val identifier: RegistryAgentIdentifier
            get() = RegistryAgentIdentifier("claim", "1.0.0", AgentRegistrySourceIdentifier.Local)
    }

    override val options: Map<String, AgentOption>
        get() = mapOf(
            AgentOption.UInt(default = 1000U).buildFullOption(
                name = "CLAIM_DELAY",
                description = "Milliseconds of delay between each claim",
                required = false
            ),
            AgentOption.ULongList().buildFullOption(
                name = "CLAIM_AMOUNTS",
                description = "An amount for each claim.  The value is specified in micro cents. $1.00 is $MICRO_CENTS_TO_DOLLARS and $0.01 is $MICRO_CENTS_TO_CENTS.",
                required = true
            ),
            AgentOption.StringList().buildFullOption(
                name = "CLAIM_DESCRIPTIONS",
                description = "A description for each claim.  If the length of this list is less than CLAIM_AMOUNTS, the last description will be used for the remaining claims.",
                required = true
            ),
            AgentOption.Boolean(default = false).buildFullOption(
                name = "AUTO_KILL",
                description = "Whether to request that this agent is automatically killed when posting a claim.",
                required = false
            ),
            AgentOption.Boolean(default = false).buildFullOption(
                name = "IGNORE_SHOULD_EXIT",
                description = "Whether to ignore the shouldExit field in the response.",
                required = false
            ),
            AgentOption.Boolean(default = false).buildFullOption(
                name = "KEEP_ALIVE",
                description = "If this is true, after all claims are made the agent will wait to be killed manually",
                required = false
            )
        )

    override val description: String
        get() = "Makes a number of claims described by CLAIM_AMOUNTS and CLAIM_DESCRIPTIONS.  After all claims have been made this agent will exit."

    override val readme: String
        get() = "TODO"

    override val summary: String
        get() = "TODO"

    override val exportSettings: Map<RuntimeId, UnresolvedAgentExportSettings>
        get() = genericExportSettings

    override suspend fun execute(
        client: Client,
        session: LocalSession,
        agent: SessionAgent
    ) {
        val delayDuration = getRequiredOption<AgentOptionValue.UInt>(agent, "CLAIM_DELAY").value.toInt().milliseconds
        val claimAmounts = getRequiredOption<AgentOptionValue.ULongList>(agent, "CLAIM_AMOUNTS").value
        val claimDescriptions = getRequiredOption<AgentOptionValue.StringList>(agent, "CLAIM_DESCRIPTIONS").value
        val autoKill = getRequiredOption<AgentOptionValue.Boolean>(agent, "AUTO_KILL").value
        val ignoreShouldExit = getRequiredOption<AgentOptionValue.Boolean>(agent, "IGNORE_SHOULD_EXIT").value
        val keepAlive = getRequiredOption<AgentOptionValue.Boolean>(agent, "KEEP_ALIVE").value

        if (claimAmounts.size != claimDescriptions.size)
            agent.logger.error { "CLAIM_AMOUNTS and CLAIM_DESCRIPTIONS must be the same size" }

        if (claimAmounts.isEmpty())
            agent.logger.error { "At least one claim must be specified" }

        for ((amount, description) in claimAmounts.zip(claimDescriptions)) {
            delay(delayDuration)

            val response = this.client.post(Rpc.Claim()) {
                contentType(ContentType.Application.Json)
                setBody(
                    AgentClaimRequest(
                        amount = AgentBudgetUnit(amount.toULong()),
                        description = description,
                        autoKill = autoKill
                    )
                )
                bearerAuth(agent.secret)
            }.body<AgentClaimResponse>()

            if (!ignoreShouldExit && response.shouldExit)
                break
        }

        if (keepAlive)
            delay(Duration.INFINITE)
    }
}
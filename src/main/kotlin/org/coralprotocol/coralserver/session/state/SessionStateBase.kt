@file:OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session.state

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.session.*
import org.coralprotocol.coralserver.util.InstantSerializer
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
@Description("The base state of a running session, without agent or threads")
open class SessionStateBase(
    @Description("The unique identifier for this session")
    val id: SessionId,

    @Description("The timestamp of when this state was generated")
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,

    @Description("The namespace that this session resides in")
    val namespace: String,

    @Description("The status of the session")
    val status: SessionStatus,

    override val annotations: Map<String, String>,
) : SessionResource


@Serializable
@Description("The state of a running session")
data class SessionStateExtended(
    @Description("Base session state")
    val base: SessionStateBase,

    @Description("A list of the states of all agents in this session")
    val agents: List<SessionAgentState>,

    @Description("A list of the states of all threads in this session")
    val threads: List<SessionThread>,

    @Description("A counter for the session's running budget.  See SessionRunningBudget for more information")
    val runningBudget: SessionRunningBudget,

    @Description("The budget settings for this session")
    val budgetSettings: SessionBudgetSettings,

    @Description("A list of receipts for all claims made by all agents in this session")
    val agentClaimReceipts: List<SessionAgentClaimReceipt>,
)

@Serializable
@JsonClassDiscriminator("type")
sealed interface SessionState {
    @Serializable
    @SerialName("base")
    data class Base(val state: SessionStateBase) : SessionState

    @Serializable
    @SerialName("extended")
    data class Extended(val state: SessionStateExtended) : SessionState
}
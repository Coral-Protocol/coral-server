package org.coralprotocol.coralserver.payment

import coral.escrow.v1.CoralEscrow
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.models.ClaimMultipleResult
import org.coralprotocol.payment.blockchain.models.ClaimResult
import org.coralprotocol.payment.blockchain.models.MintInfo
import org.coralprotocol.payment.blockchain.models.RefundResult
import org.coralprotocol.payment.blockchain.models.Session
import org.coralprotocol.payment.blockchain.models.SessionInfo
import org.coralprotocol.payment.blockchain.models.TransactionResult
import kotlin.Result

class BlankBlockchainService : BlockchainService {
    override suspend fun checkEscrowClaimed(
        sessionId: Long,
        authorityPubkey: String,
        agentId: String
    ): Result<Boolean> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun createAndFundEscrowSession(
        agents: List<CoralEscrow.AgentConfig>,
        mintPubkey: String,
        fundingAmount: Long,
        sessionId: Long?
    ): Result<SessionInfo> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun createDevnetATA(
        mintPubkey: String,
        ownerPubkey: String
    ): Result<String> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun createDevnetMint(): Result<MintInfo> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun createEscrowSession(
        agents: List<CoralEscrow.AgentConfig>,
        mintPubkey: String,
        sessionId: Long?
    ): Result<SessionInfo> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun fundEscrowSession(
        sessionId: Long,
        amount: Long
    ): Result<TransactionResult> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun getAllSessions(): Result<List<Session>> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun getEscrowSession(
        sessionId: Long,
        authorityPubkey: String
    ): Result<Session?> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun mintDevnetTokensTo(
        mintPubkey: String,
        destinationPubkey: String,
        amount: Long
    ): Result<TransactionResult> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun refundEscrowLeftover(
        sessionId: Long,
        mintPubkey: String
    ): Result<RefundResult> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun submitClaimMultiple(
        sessionId: Long,
        claims: List<Pair<String, Long>>,
        authorityPubKey: String
    ): Result<ClaimMultipleResult> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun submitEscrowClaim(
        sessionId: Long,
        agentId: String,
        amount: Long,
        authorityPubkey: String
    ): Result<ClaimResult> {
        return Result.failure(NotImplementedError())
    }
}
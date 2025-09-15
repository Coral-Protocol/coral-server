//package org.coralprotocol.coralserver.server
//import org.coralprotocol.coralserver.escrow.blockchain.BlockchainServiceImpl
//import org.coralprotocol.coralserver.escrow.blockchain.models.SignerConfig
//import org.coralprotocol.coralserver.escrow.blockchain.builders.CoralRequestBuilders
//import kotlinx.coroutines.runBlocking
//import kotlinx.coroutines.delay
//import java.io.File
//

// TODO: Use the other demo as a reference
///**
// * Standalone demo using the distributed JAR to run a complete escrow flow on devnet.
// *
// * Prerequisites:
// * 1. The coral-blockchain JAR must be published to local Maven repository
// * 2. You need a funded wallet on devnet (will use testkey/master.json)
// * 3. The program must be deployed and initialized on devnet
// */
//fun main() = runBlocking {
//    println("=" * 60)
//    println("🌊 CORAL ESCROW - DEMO")
//    println("=" * 60)
//    println()
//
//    // Configuration
//    val keypairPath = "../../testkey/master.json"
//    val rpcUrl = "https://api.devnet.solana.com" // Default to localhost
//
//    // Check if keypair exists
//    if (!File(keypairPath).exists()) {
//        println("❌ Keypair not found at: $keypairPath")
//        println("   Please ensure you have a funded keypair at this location")
//        return@runBlocking
//    }
//
//    println("🔑 Using keypair: $keypairPath")
//    val network = when {
//        rpcUrl.contains("devnet") -> "Devnet"
//        rpcUrl.contains("localhost") || rpcUrl.contains("127.0.0.1") -> "Localhost"
//        rpcUrl.contains("mainnet") -> "Mainnet"
//        else -> "Custom"
//    }
//    println("🌐 Network: $network ($rpcUrl)")
//    println()
//
//    // Create blockchain service
//    val signerConfig = SignerConfig.File(keypairPath)
//    val blockchainService = BlockchainServiceImpl(rpcUrl, "confirmed", signerConfig)
//
//    // Get authority pubkey
//    val authorityPubkey = blockchainService.getAuthorityPubkey()
//    println("👤 Authority pubkey: $authorityPubkey")
//    println()
//
//    // Check program configuration
//    println("🔧 Checking program configuration...")
//    val configResult = blockchainService.getProgramConfig()
//
//    if (configResult.isFailure) {
//        println("❌ Failed to check config: ${configResult.exceptionOrNull()?.message}")
//        return@runBlocking
//    }
//
//    val config = configResult.getOrThrow()
//    if (config == null) {
//        if (rpcUrl.contains("localhost") || rpcUrl.contains("127.0.0.1")) {
//            // On localhost, auto-initialize for convenience
//            println("📝 Config not initialized, setting up...")
//
//            val maxSessionValue = 1_000_000_000L
//            val initResult = blockchainService.initProgramConfig(maxSessionValue)
//
//            if (initResult.isSuccess) {
//                val newConfig = initResult.getOrThrow()
//                println("✅ Config initialized!")
//                println("   Admin: ${newConfig.adminPubkey}")
//                println("   Max session value: ${newConfig.maxSessionValue}")
//            } else {
//                println("❌ Failed to initialize config: ${initResult.exceptionOrNull()?.message}")
//                return@runBlocking
//            }
//        } else {
//            println("❌ Program not initialized!")
//            println()
//            println("Please initialize the program first using the Admin CLI:")
//            println("1. cd coral-kotlin")
//            println("2. ./gradlew :examples:adminCLI --args=\"init-config 1000000000\"")
//            return@runBlocking
//        }
//    } else {
//        println("✅ Program initialized")
//        println("   Admin: ${config.adminPubkey}")
//        println("   Paused: ${config.paused}")
//        println("   Max session value: ${config.maxSessionValue} lamports")
//
//        if (config.paused) {
//            println()
//            println("⚠️  Program is currently paused!")
//            println("   Please unpause it using the Admin CLI:")
//            println("   ./gradlew :examples:adminCLI --args=\"update-config --unpause\"")
//            return@runBlocking
//        }
//    }
//
//    println()
//    println("=" * 60)
//    println("💼 STARTING ESCROW DEMO")
//    println("=" * 60)
//    println()
//
//    // Create a new SPL token mint
//    println("🪙 Creating new SPL token mint...")
//    val mintResult = blockchainService.createMint()
//    if (mintResult.isFailure) {
//        println("❌ Failed to create mint: ${mintResult.exceptionOrNull()?.message}")
//        println("   Make sure your wallet has enough SOL for transactions")
//        return@runBlocking
//    }
//
//    val mintInfo = mintResult.getOrThrow()
//    val mintPubkey = mintInfo.mintPubkey
//    println("✅ Created mint: $mintPubkey")
//    println("   Decimals: ${mintInfo.decimals}")
//
//    // Check if mint is allowed
//    println()
//    println("🔍 Checking mint allowlist...")
//    val isAllowedResult = blockchainService.isMintAllowed(mintPubkey)
//    if (isAllowedResult.isFailure) {
//        println("❌ Failed to check allowlist: ${isAllowedResult.exceptionOrNull()?.message}")
//        return@runBlocking
//    }
//
//    if (!isAllowedResult.getOrThrow()) {
//        println("📝 Mint not in allowlist, adding it...")
//        val addResult = blockchainService.addAllowedMint(mintPubkey)
//        if (addResult.isFailure) {
//            println("❌ Failed to add mint to allowlist: ${addResult.exceptionOrNull()?.message}")
//            println("   You may need admin privileges to add mints")
//            return@runBlocking
//        }
//        println("✅ Mint added to allowlist")
//
//        // Small delay to ensure transaction is confirmed
//        delay(2000)
//    } else {
//        println("✅ Mint already in allowlist")
//    }
//
//    // Create ATA for authority
//    println()
//    println("💳 Creating Associated Token Account for authority...")
//    val ataResult = blockchainService.createATA(mintPubkey, authorityPubkey)
//    if (ataResult.isFailure) {
//        println("❌ Failed to create ATA: ${ataResult.exceptionOrNull()?.message}")
//        return@runBlocking
//    }
//    val authorityAta = ataResult.getOrThrow()
//    println("✅ Created ATA: $authorityAta")
//
//    // Mint tokens to authority
//    println()
//    println("💰 Minting tokens to authority...")
//    val mintAmount = 1_000_000L
//    val mintToResult = blockchainService.mintTo(mintPubkey, authorityAta, mintAmount)
//    if (mintToResult.isFailure) {
//        println("❌ Failed to mint tokens: ${mintToResult.exceptionOrNull()?.message}")
//        return@runBlocking
//    }
//    println("✅ Minted ${mintAmount} tokens")
//
//    // Define agents for the session
//    // In this demo, authority acts as the developer for all agents
//    val agents = listOf(
//        CoralRequestBuilders.agent("data_analyst", 100_000, authorityPubkey),
//        CoralRequestBuilders.agent("ml_engineer", 200_000, authorityPubkey),
//        CoralRequestBuilders.agent("qa_tester", 50_000, authorityPubkey)
//    )
//
//    println()
//    println("=" * 60)
//    println("📋 CREATING ESCROW SESSION")
//    println("=" * 60)
//
//    println()
//    println("👥 Agents in session:")
//    agents.forEach { agent ->
//        println("   - ${agent.id}: max ${agent.cap} tokens")
//    }
//    val totalCap = agents.sumOf { it.cap }
//    println("   Total cap: $totalCap tokens")
//
//    // Create session
//    println()
//    println("🚀 Creating escrow session...")
//    val sessionResult = blockchainService.createSession(agents, mintPubkey)
//    if (sessionResult.isFailure) {
//        println("❌ Failed to create session: ${sessionResult.exceptionOrNull()?.message}")
//        return@runBlocking
//    }
//
//    val sessionInfo = sessionResult.getOrThrow()
//    val sessionId = sessionInfo.sessionId
//    println("✅ Session created!")
//    println("   Session ID: $sessionId")
//    println("   Transaction: ${sessionInfo.transactionHash}")
//
//    // Fund the session
//    println()
//    println("💸 Funding the session...")
//    val fundResult = blockchainService.fundSession(sessionId, totalCap)
//    if (fundResult.isFailure) {
//        println("❌ Failed to fund session: ${fundResult.exceptionOrNull()?.message}")
//        return@runBlocking
//    }
//
//    println("✅ Session funded with $totalCap tokens")
//    println("   Transaction: ${fundResult.getOrThrow().signature}")
//
//    // Wait for confirmations
//    println()
//    println("⏳ Waiting for blockchain confirmations...")
//    delay(3000)
//
//    println()
//    println("=" * 60)
//    println("💼 AGENTS CLAIMING PAYMENTS")
//    println("=" * 60)
//
//    // Agent claims
//    println()
//    println("🎯 Processing agent claims...")
//
//    // Data analyst claims
//    println()
//    println("📊 Data analyst claiming payment...")
//    val claim1 = blockchainService.submitClaim(sessionId, "data_analyst", 80_000)
//    if (claim1.isSuccess) {
//        val result = claim1.getOrThrow()
//        println("✅ Data analyst claimed ${result.amountClaimed} tokens")
//        println("   Transaction: ${result.signature}")
//    } else {
//        println("❌ Claim failed: ${claim1.exceptionOrNull()?.message}")
//    }
//
//    // ML engineer claims
//    println()
//    println("🤖 ML engineer claiming payment...")
//    val claim2 = blockchainService.submitClaim(sessionId, "ml_engineer", 150_000)
//    if (claim2.isSuccess) {
//        val result = claim2.getOrThrow()
//        println("✅ ML engineer claimed ${result.amountClaimed} tokens")
//        println("   Transaction: ${result.signature}")
//    } else {
//        println("❌ Claim failed: ${claim2.exceptionOrNull()?.message}")
//    }
//
//    // QA tester claims
//    println()
//    println("🧪 QA tester claiming payment...")
//    val claim3 = blockchainService.submitClaim(sessionId, "qa_tester", 40_000)
//    if (claim3.isSuccess) {
//        val result = claim3.getOrThrow()
//        println("✅ QA tester claimed ${result.amountClaimed} tokens")
//        println("   Transaction: ${result.signature}")
//    } else {
//        println("❌ Claim failed: ${claim3.exceptionOrNull()?.message}")
//    }
//
//    // Test error handling
//    println()
//    println("=" * 60)
//    println("🧪 TESTING ERROR HANDLING")
//    println("=" * 60)
//
//    println()
//    println("📌 Test 1: Duplicate claim (should fail)")
//    val duplicateClaim = blockchainService.submitClaim(sessionId, "data_analyst", 10_000)
//    if (duplicateClaim.isFailure) {
//        println("✅ Correctly blocked: ${duplicateClaim.exceptionOrNull()?.message}")
//    } else {
//        println("⚠️  Unexpected: Duplicate claim succeeded!")
//    }
//
//    println()
//    println("📌 Test 2: Unknown agent claim (should fail)")
//    val unknownClaim = blockchainService.submitClaim(sessionId, "hacker", 10_000)
//    if (unknownClaim.isFailure) {
//        println("✅ Correctly blocked: ${unknownClaim.exceptionOrNull()?.message}")
//    } else {
//        println("⚠️  Unexpected: Unknown agent claim succeeded!")
//    }
//
//    println()
//    println("📌 Test 3: Over-cap claim (should fail)")
//    val overCapClaim = blockchainService.submitClaim(sessionId, "qa_tester", 20_000)
//    if (overCapClaim.isFailure) {
//        println("✅ Correctly blocked: ${overCapClaim.exceptionOrNull()?.message}")
//    } else {
//        println("⚠️  Unexpected: Over-cap claim succeeded!")
//    }
//
//    // Refund leftover
//    println()
//    println("=" * 60)
//    println("💸 CLOSING SESSION & REFUND")
//    println("=" * 60)
//
//    println()
//    println("🔄 Refunding leftover funds...")
//    val refundResult = blockchainService.refundLeftover(sessionId, mintPubkey)
//    if (refundResult.isSuccess) {
//        val refund = refundResult.getOrThrow()
//        println("✅ Session closed and funds refunded")
//        println("   Amount refunded: ${refund.amountRefunded} tokens")
//        println("   Transaction: ${refund.signature}")
//    } else {
//        println("❌ Refund failed: ${refundResult.exceptionOrNull()?.message}")
//    }
//
//    println()
//    println("=" * 60)
//    println("🎉 DEMO COMPLETE!")
//    println("=" * 60)
//    println()
//    println("Summary:")
//    println("✅ Connected to devnet using distributed JAR")
//    println("✅ Created and managed SPL token mint")
//    println("✅ Created escrow session with 3 agents")
//    println("✅ Funded session with $totalCap tokens")
//    println("✅ Processed agent claims")
//    println("✅ Demonstrated error handling")
//    println("✅ Closed session and refunded leftover")
//    println()
//    println("The distributed JAR makes it easy to integrate Coral Protocol")
//    println("into any JVM-based application without manual FFI setup!")
//}
//
//// Extension function for string repetition
//operator fun String.times(n: Int): String = this.repeat(n)
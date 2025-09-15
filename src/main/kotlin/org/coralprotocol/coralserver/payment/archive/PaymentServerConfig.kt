//package org.coralprotocol.coralserver.payment.config
//
//import org.coralprotocol.payment.blockchain.models.SignerConfig
//import com.sksamuel.hoplite.ConfigAlias
//
//data class PaymentServerConfig(
//    val server: ServerConfig,
//    val blockchain: BlockchainConfig,
//    val app: AppConfig? = null,
//    val agent: AgentConfig? = null,
//    val notifications: NotificationConfig? = null
//)
//
//data class ServerConfig(
//    val mode: ServerMode,
//    val port: Int = 8080
//)
//
//enum class ServerMode {
//    @ConfigAlias("app")
//    APP,
//    @ConfigAlias("agent")
//    AGENT
//}
//
//data class BlockchainConfig(
//    @ConfigAlias("rpc_url")
//    val rpcUrl: String,
//    val commitment: String = "confirmed",
//    val signer: SignerConfig
//)
//
//data class AppConfig(
//    @ConfigAlias("auto_fund")
//    val autoFund: AutoFundConfig
//)
//
//data class AutoFundConfig(
//    val enabled: Boolean,
//    @ConfigAlias("max_per_session")
//    val maxPerSession: Long
//)
//
//data class AgentConfig(
//    @ConfigAlias("agent_id")
//    val agentId: String,
//    @ConfigAlias("max_concurrent_sessions")
//    val maxConcurrentSessions: Int? = 5,
//    @ConfigAlias("scan_interval")
//    val scanInterval: String = "30s",
//    @ConfigAlias("auto_claim")
//    val autoClaim: AutoClaimConfig
//)
//
//data class AutoClaimConfig(
//    val enabled: Boolean,
//    @ConfigAlias("min_amount")
//    val minAmount: Long = 100
//)
//
//data class NotificationConfig(
//    val timeout: String = "5s",
//    @ConfigAlias("max_retries")
//    val maxRetries: Int = 3,
//    @ConfigAlias("retry_delay")
//    val retryDelay: String = "1s"
//)
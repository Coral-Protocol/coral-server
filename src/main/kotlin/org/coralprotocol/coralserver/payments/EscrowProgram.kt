package org.coralprotocol.coralserver.payments

import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface EscrowProgram : Library {
    fun coral_setup(payerKeypath: String): Byte
    fun coral_generate_mint(): Pointer  // CString*
    fun coral_generate_agent_ata_for_mint(mint: String): Pointer  // CString*
    fun coral_init_session(agentAtas: String, caps: String, mint: String): Byte
    fun coral_deposit(mint: String, amount: ULong): Byte
    fun coral_claim(agentAta: String,mint: String, amount: ULong): Byte
    fun coral_string_free(ptr: Pointer)
    fun coral_refund_leftover(mint: String)
}

data class AgentPayCtx(
    val amount: ULong,
    val ata: String,
)

data class PaymentCtx(
    var agentsToAtas: Map<String, AgentPayCtx> = emptyMap(),
    var mint: String = "",
)

object EscrowProgramLib {
    private val ctx = PaymentCtx()
    val escrow: EscrowProgram = Native.load("coral_ffi", EscrowProgram::class.java, mapOf(
        Library.OPTION_FUNCTION_MAPPER to FunctionMapper { lib: NativeLibrary, method ->
            method.name.substringBefore('-')
        }
    ))

    private val ffiDispatcher = Executors.newSingleThreadExecutor {
        Thread(it, "escrow-ffi").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    suspend fun initAndDeposit(agentsToAmounts: Map<String, ULong>) = withContext(ffiDispatcher) {
        require(escrow.coral_setup("/Users/andri/Projects/coral-server/src/main/resources/master.json").toInt()==0)

        val mintPtr = escrow.coral_generate_mint()
        val mint = mintPtr.getString(0)
        escrow.coral_string_free(mintPtr)

        val agentsToPayCtx = mutableMapOf<String, AgentPayCtx>()
        agentsToAmounts.forEach { (agentId, amount) ->
            val ataPtr1 = escrow.coral_generate_agent_ata_for_mint(mint)
            val ata1    = ataPtr1.getString(0)
            escrow.coral_string_free(ataPtr1)
            agentsToPayCtx[agentId] = AgentPayCtx(amount, ata1)
        }
        val atas = agentsToPayCtx.map { (k, v) -> v.ata }.joinToString(",") { it }
        val caps = agentsToPayCtx.map { (k, v) -> v.amount }.joinToString(",") { it.toString() }
        val amount = agentsToPayCtx.map { (k, v) -> v.amount }.sum()

        escrow.coral_init_session(atas, caps, mint)
        escrow.coral_deposit(mint, amount)

        ctx.agentsToAtas = agentsToPayCtx
        ctx.mint = mint
    }

    suspend fun claim(id: String, amount: ULong) = withContext(ffiDispatcher) {
        escrow.coral_claim(ctx.agentsToAtas[id]?.ata ?: error("Call initAndDeposit"), ctx.mint, amount)
    }

    fun createDemoEscrow(agentsToAmounts: Map<String, ULong>) {
        CoroutineScope(Dispatchers.Default).launch {
            delay(1.minutes)

            initAndDeposit(agentsToAmounts)

            delay(1.minutes)

            ctx.agentsToAtas.forEach { (id, agentCtx) ->
                claim(id, (agentCtx.amount.toDouble() * Random.nextDouble(from = 0.5, until = 1.0)).toULong())
            }

            //escrow.coral_refund_leftover(ctx.mint)
        }
    }
}

fun coralEscrowProgramDemo() {
    val escrow = EscrowProgramLib.escrow

    require(escrow.coral_setup("/Users/andri/Projects/coral-server/src/main/resources/master.json").toInt()==0)

    // 2) create mint
    val mintPtr = escrow.coral_generate_mint()
    val mint = mintPtr.getString(0)
    escrow.coral_string_free(mintPtr)

    val ataPtr1 = escrow.coral_generate_agent_ata_for_mint(mint)
    val ata1    = ataPtr1.getString(0)
    escrow.coral_string_free(ataPtr1)

    val ataPtr2 = escrow.coral_generate_agent_ata_for_mint(mint)
    val ata2    = ataPtr2.getString(0)
    escrow.coral_string_free(ataPtr2)

    // 3) init session (two agent ATAs)
    val atAs = "$ata1,$ata2"
    val caps = "100,200"
    require(escrow.coral_init_session(atAs, caps, mint).toInt()==0)

    // 4) deposit 300
    require(escrow.coral_deposit(mint, 300u).toInt()==0)

    // 5) claim each
    require(escrow.coral_claim(ata1, mint, 100u).toInt()==0)
    require(escrow.coral_claim(ata2, mint, 200u).toInt()==0)

    println("🏆 success!")
}
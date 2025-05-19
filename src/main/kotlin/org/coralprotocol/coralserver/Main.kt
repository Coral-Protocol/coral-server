package org.coralprotocol.coralserver

import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Start sse-server mcp on port 5555.
 *
 * @param args
 * - "--stdio": Runs an MCP server using standard input/output.
 * - "--sse-server <port>": Runs an SSE MCP server with a plain configuration.
 */

interface Coral : Library {
    fun coral_setup(payerKeypath: String): Byte
    fun coral_generate_mint(): Pointer  // CString*
    fun coral_generate_agent_ata_for_mint(mint: String): Pointer  // CString*
    fun coral_init_session(agentAtas: String, caps: String, mint: String): Byte
    fun coral_deposit(mint: String, amount: ULong): Byte
    fun coral_claim(agentAta: String,mint: String, amount: ULong): Byte
    fun coral_string_free(ptr: Pointer)
}

object CoralLib {
    val INSTANCE: Coral = Native.load("coral_ffi", Coral::class.java, mapOf(
        Library.OPTION_FUNCTION_MAPPER to FunctionMapper { lib: NativeLibrary, method ->
            method.name.substringBefore('-')
        }
    ))
}

fun main(args: Array<String>) {
    coralEscrowProgramDemo()
}

private fun coralEscrowProgramDemo() {
    val escrow = CoralLib.INSTANCE

    require(escrow.coral_setup("/Users/andri/.config/solana/id.json").toInt()==0)

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
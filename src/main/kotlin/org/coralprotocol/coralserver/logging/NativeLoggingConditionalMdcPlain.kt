package org.coralprotocol.coralserver.logging

open class NativeLoggingConditionalMdcPlain : NativeLoggingConditionalMdc() {
    override val useColor: Boolean = false
}
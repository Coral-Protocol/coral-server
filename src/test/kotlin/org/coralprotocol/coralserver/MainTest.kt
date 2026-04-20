package org.coralprotocol.coralserver

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.net.BindException
import java.net.ServerSocket

class MainTest : FunSpec({
    test("ensureServerPortAvailable allows ephemeral ports") {
        ensureServerPortAvailable("127.0.0.1", 0)
    }

    test("ensureServerPortAvailable throws a clear error when the port is occupied") {
        ServerSocket(0).use { socket ->
            val port = socket.localPort

            val exception = shouldThrow<IllegalStateException> {
                ensureServerPortAvailable("127.0.0.1", port)
            }

            exception.message.shouldContain("127.0.0.1:$port")
            exception.cause.shouldBeInstanceOf<BindException>()
        }
    }
})

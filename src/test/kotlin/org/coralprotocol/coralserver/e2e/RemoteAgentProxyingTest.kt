package org.coralprotocol.coralserver.e2e

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

class RemoteAgentProxyingTest {
    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testRemoteAgentProxying(): Unit {
        shouldNotThrowAny {
            testApplication {
                val exportingServer = TestCoralServer(port = 14391u, devmode = true).apply { setup() }
                application {
                    exportingServer.
                }
                val importingServer = TestCoralServer(port = 14392u, devmode = true).apply { setup() }

                importingServer.sessionManager.getOrCreateSession("test", "aaa", "aaa", null)
                importingServer

            }
        }
    }
}

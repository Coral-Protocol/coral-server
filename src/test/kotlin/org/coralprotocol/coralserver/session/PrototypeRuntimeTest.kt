package org.coralprotocol.coralserver.session

import io.kotest.core.NamedTag
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeModelProvider
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeString
import org.coralprotocol.coralserver.utils.TestMcpServer
import org.coralprotocol.coralserver.utils.multiAgentPayloadTest
import org.coralprotocol.coralserver.utils.runTestServerTest
import org.koin.core.component.get

class PrototypeRuntimeTest : CoralTest({
    val modelProvider = System.getenv("CORAL_TEST_OPENAI_API_KEY")?.let {
        PrototypeModelProvider.OpenAI(
            PrototypeString.Inline(it),
            PrototypeString.Inline("gpt-4.1-mini")
        )
    }

    test("testMultiAgentPayload").config(enabled = modelProvider != null) {
        multiAgentPayloadTest(modelProvider!!)
    }

    test("testCustomMcpServerNoAuth").config(enabled = modelProvider != null, tags = setOf(NamedTag("debug"))) {
        val server = TestMcpServer()
        runTestServerTest(modelProvider!!, server, server.asPrototypeToolServer(get()))
    }

    test("testCustomMcpServerUrlAuth").config(enabled = modelProvider != null, tags = setOf(NamedTag("debug"))) {
        val server = TestMcpServer()
        runTestServerTest(modelProvider!!, server, server.asPrototypeToolServerParamAuth(get()))
    }

    test("testCustomMcpServerBearerAuth").config(enabled = modelProvider != null, tags = setOf(NamedTag("debug"))) {
        val server = TestMcpServer()
        runTestServerTest(modelProvider!!, server, server.asPrototypeToolServerBearerAuth(get()))
    }
})
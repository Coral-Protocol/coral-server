package org.coralprotocol.coralserver.session

import io.kotest.core.NamedTag
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.utils.TestMcpServer
import org.coralprotocol.coralserver.utils.multiAgentPayloadTest
import org.coralprotocol.coralserver.utils.runTestServerTest
import org.koin.core.component.get

class PrototypeRuntimeTest : CoralTest({
    val model = "gpt-4.1-mini"

    test("testMultiAgentPayload").config(enabledIf = ::hasOpenAIProxy) {
        multiAgentPayloadTest(openAIProxy!!, model)
    }

    test("testCustomMcpServerNoAuth").config(enabledIf = ::hasOpenAIProxy, tags = setOf(NamedTag("debug"))) {
        val server = TestMcpServer()
        runTestServerTest(openAIProxy!!, model, server, server.asPrototypeToolServer(get()))
    }

    test("testCustomMcpServerUrlAuth").config(enabledIf = ::hasOpenAIProxy, tags = setOf(NamedTag("debug"))) {
        val server = TestMcpServer()
        runTestServerTest(openAIProxy!!, model, server, server.asPrototypeToolServerParamAuth(get()))
    }

    test("testCustomMcpServerBearerAuth").config(enabledIf = ::hasOpenAIProxy, tags = setOf(NamedTag("debug"))) {
        val server = TestMcpServer()
        runTestServerTest(openAIProxy!!, model, server, server.asPrototypeToolServerBearerAuth(get()))
    }
})
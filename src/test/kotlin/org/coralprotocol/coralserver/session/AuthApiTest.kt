package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.ktor.client.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.routes.api.v1.Auth
import org.coralprotocol.coralserver.routes.api.v1.Registry
import org.coralprotocol.coralserver.routes.ws.v1.Events
import org.koin.core.component.inject

class AuthApiTest : CoralTest({
    test("testAuthSession") {
        val client by inject<HttpClient>()

        client.get(Registry()).shouldHaveStatus(HttpStatusCode.Unauthorized)
        client.submitForm(
            url = client.href(Auth.Token()),
            formParameters = parameters {
                append("token", authToken)
            }
        ).shouldHaveStatus(HttpStatusCode.Found)
        client.get(Registry()).shouldHaveStatus(HttpStatusCode.OK)
    }

    test("testAuthWebSocket") {
        val client by inject<HttpClient>()

        client.get(Events.SessionEvents(namespace = "test", sessionId = "test"))
            .shouldHaveStatus(HttpStatusCode.Unauthorized)
        client.submitForm(
            url = client.href(Auth.Token()),
            formParameters = parameters {
                append("token", authToken)
            }
        ).shouldHaveStatus(HttpStatusCode.Found)
        client.get(Events.SessionEvents(namespace = "test", sessionId = "test"))
            .shouldHaveStatus(HttpStatusCode.NotFound)
    }
})
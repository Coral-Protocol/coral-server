package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.coralprotocol.coralserver.routes.api.v1.Auth
import org.coralprotocol.coralserver.routes.api.v1.Registry
import org.coralprotocol.coralserver.routes.ws.v1.Events

class AuthApiTest : FunSpec({
    test("testAuthSession") {
        sessionTest {
            ktor.client.get(Registry()).shouldHaveStatus(HttpStatusCode.Unauthorized)
            ktor.client.submitForm(
                url = ktor.client.href(Auth.Token()),
                formParameters = parameters {
                    append("token", authToken)
                }
            ).shouldHaveStatus(HttpStatusCode.Found)
            ktor.client.get(Registry()).shouldHaveStatus(HttpStatusCode.OK)
        }
    }

    test("testAuthWebSocket") {
        sessionTest {
            ktor.client.get(Events.SessionEvents(namespace = "test", sessionId = "test"))
                .shouldHaveStatus(HttpStatusCode.Unauthorized)
            ktor.client.submitForm(
                url = ktor.client.href(Auth.Token()),
                formParameters = parameters {
                    append("token", authToken)
                }
            ).shouldHaveStatus(HttpStatusCode.Found)
            ktor.client.get(Events.SessionEvents(namespace = "test", sessionId = "test"))
                .shouldHaveStatus(HttpStatusCode.NotFound)
        }
    }
})
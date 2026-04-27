package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.routes.api.v1.Templates
import org.coralprotocol.coralserver.session.state.SessionStateExtended
import org.coralprotocol.coralserver.template.SessionTemplateInfo
import org.coralprotocol.coralserver.template.TemplateLaunchRequest
import org.koin.test.inject

class TemplateApiTest : CoralTest({
    test("list templates includes network-service") {
        val client by inject<HttpClient>()

        val templates: List<SessionTemplateInfo> = client.authenticatedGet(Templates()).shouldBeOK().body()
        templates.map { it.slug }.shouldContain("network-service")
    }

    test("preview network-service template returns deferred empty session request") {
        val client by inject<HttpClient>()

        val request: SessionRequest = client.authenticatedPost(
            Templates.BySlug.Preview(Templates.BySlug(slug = "network-service"))
        ) {
            setBody(
                TemplateLaunchRequest(
                    namespace = "template-preview",
                    parameters = mapOf("MOCK_MODE" to "true"),
                )
            )
        }.shouldBeOK().body()

        request.agentGraphRequest.agents.size.shouldBeEqual(0)
        request.execution.shouldBeEqual(SessionRequestExecution.Defer)
        request.annotations.shouldContain("template" to "network-service")
        request.annotations.shouldContain("MOCK_MODE" to "true")
    }

    test("launch network-service template creates a deferred session") {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val namespace = "template-launch"

        val sessionId: SessionIdentifier = client.authenticatedPost(
            Templates.BySlug.Launch(Templates.BySlug(slug = "network-service"))
        ) {
            setBody(TemplateLaunchRequest(namespace = namespace))
        }.shouldBeOK().body()

        sessionId.namespace.shouldBeEqual(namespace)

        val session = localSessionManager.getSessions(namespace).find { it.id == sessionId.sessionId }.shouldNotBeNull()
        session.status.value.shouldBeEqual(SessionStatus.PendingExecution)
        session.agents.size.shouldBeEqual(0)

        val state: SessionStateExtended = client.authenticatedGet(
            org.coralprotocol.coralserver.routes.api.v1.LocalSessions.Session.Existing.Extended(
                org.coralprotocol.coralserver.routes.api.v1.LocalSessions.Session.Existing(
                    namespace = namespace,
                    sessionId = sessionId.sessionId,
                )
            )
        ).shouldBeOK().body()
        state.base.status.shouldBeEqual(SessionStatus.PendingExecution)
    }

    test("missing template returns 404") {
        val client by inject<HttpClient>()

        client.authenticatedGet(Templates.BySlug(slug = "missing-template"))
            .shouldHaveStatus(io.ktor.http.HttpStatusCode.NotFound)
    }
})

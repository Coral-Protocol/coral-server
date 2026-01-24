package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.resources.delete
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.mcp.tools.*
import org.coralprotocol.coralserver.routes.ApiV1
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionException
import org.koin.ktor.ext.inject

@Resource("puppet")
class Puppet(val parent: ApiV1 = ApiV1()) {
    @Resource("{namespace}/{sessionId}/{agentName}")
    class Agent(
        val parent: Puppet = Puppet(),
        val namespace: String,
        val sessionId: String,
        val agentName: UniqueAgentName
    ) {
        @Resource("thread")
        class Thread(val parent: Agent) {
            @Resource("message")
            class Message(val parent: Thread)

            @Resource("participant")
            class Participant(val parent: Thread)
        }
    }
}

fun Route.puppetApi() {
    val localSessionManager by inject<LocalSessionManager>()

    fun getAgent(path: Puppet.Agent): SessionAgent {
        try {
            val session = localSessionManager.getSessions(path.namespace).firstOrNull { it.id == path.sessionId }
                ?: throw RouteException(HttpStatusCode.NotFound, "Invalid session ID")

            return session.getAgent(path.agentName)
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        } catch (e: SessionException.MissingAgentException) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }
    }

    fun RequestConfig.agentParams() {
        pathParameter<String>("namespace") {
            description = "The session's namespace"
        }
        pathParameter<String>("sessionId") {
            description = "The session's ID"
        }
        pathParameter<String>("agentName") {
            description = "The agent's name"
        }
    }

    post<Puppet.Agent.Thread>({
        summary = "Create thread"
        description = "Creates a new thread masquerading as the specified agent"
        operationId = "puppetCreateThread"
        securitySchemeNames("token")
        request {
            body<CreateThreadInput> {
                description = "Thread creation input"
            }
            agentParams()
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<CreateThreadOutput> {
                    description = "Thread creation output"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Agent not found"
                body<RouteException>()
            }
        }
    }) { path ->
        val agent = getAgent(path.parent)
        val input = call.receive<CreateThreadInput>()

        try {
            call.respond(
                CreateThreadOutput(
                    agent.session.createThread(input.threadName, agent.name, input.participantNames.toSet())
                )
            )
        } catch (e: SessionException.MissingAgentException) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }
    }

    delete<Puppet.Agent.Thread>({
        summary = "Close thread"
        description = "Closes a thread masquerading as the specified agent"
        operationId = "puppetCloseThread"
        securitySchemeNames("token")
        request {
            body<CloseThreadInput> {
                description = "Thread close input"
            }
            agentParams()
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
            }
            HttpStatusCode.NotFound to {
                description = "Agent or thread not found"
                body<RouteException>()
            }
            HttpStatusCode.BadRequest to {
                description = "Thread cannot be closed"
                body<RouteException>()
            }
        }
    }) { path ->
        val agent = getAgent(path.parent)
        val input = call.receive<CloseThreadInput>()

        try {
            val thread = agent.session.getThreadById(input.threadId)
            thread.close(agent, input.summary)

            call.respond(HttpStatusCode.OK)
        } catch (e: SessionException.MissingThreadException) {
            throw RouteException(HttpStatusCode.NotFound, e)
        } catch (e: SessionException.ThreadClosedException) {
            throw RouteException(HttpStatusCode.BadRequest, e)
        }
    }

    post<Puppet.Agent.Thread.Message>({
        summary = "Send message"
        description = "Sends a message in a thread masquerading as the specified agent"
        operationId = "puppetSendMessage"
        securitySchemeNames("token")
        request {
            body<SendMessageInput> {
                description = "Message input"
            }
            agentParams()
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<SendMessageOutput> {
                    description = "The sent message"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Agent or thread not found"
                body<RouteException>()
            }
            HttpStatusCode.BadRequest to {
                description = "Bad message"
                body<RouteException>()
            }
        }
    }) { path ->
        val agent = getAgent(path.parent.parent)
        val input = call.receive<SendMessageInput>()

        try {
            call.respond(
                SendMessageOutput(
                    status = "Message sent successfully",
                    message = agent.sendMessage(input.content, input.threadId, input.mentions.toSet())
                )
            )
        } catch (e: SessionException.MissingThreadException) {
            throw RouteException(HttpStatusCode.NotFound, e)
        } catch (e: SessionException.MissingAgentException) {
            throw RouteException(HttpStatusCode.NotFound, e)
        } catch (e: SessionException.ThreadClosedException) {
            throw RouteException(HttpStatusCode.BadRequest, e)
        } catch (e: SessionException.IllegalThreadMentionException) {
            throw RouteException(HttpStatusCode.BadRequest, e)
        }
    }

    post<Puppet.Agent.Thread.Participant>({
        summary = "Add thread participant"
        description = "Adds an agent to a thread masquerading as the specified agent"
        operationId = "puppetAddThreadParticipant"
        securitySchemeNames("token")
        request {
            body<AddParticipantInput> {
                description = "Thread and participant information details"
            }
            agentParams()
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
            }
            HttpStatusCode.NotFound to {
                description = "Agent or thread not found"
                body<RouteException>()
            }
            HttpStatusCode.BadRequest to {
                description = "Participant cannot be added"
                body<RouteException>()
            }
        }
    }) { path ->
        val agent = getAgent(path.parent.parent)
        val input = call.receive<AddParticipantInput>()

        try {
            val thread = agent.session.getThreadById(input.threadId)
            thread.addParticipant(agent, agent.session.getAgent(input.participantName))

            call.respond(HttpStatusCode.OK)
        } catch (e: SessionException.MissingThreadException) {
            throw RouteException(HttpStatusCode.NotFound, e)
        } catch (e: SessionException.MissingAgentException) {
            throw RouteException(HttpStatusCode.NotFound, e)
        } catch (e: SessionException.AlreadyParticipatingException) {
            throw RouteException(HttpStatusCode.BadRequest, e)
        } catch (e: SessionException.NotParticipatingException) {
            throw RouteException(HttpStatusCode.BadRequest, e)
        }
    }

    delete<Puppet.Agent.Thread.Participant>({
        summary = "Remove thread participant"
        description = "Removes an agent from a thread masquerading as the specified agent"
        operationId = "puppetRemoveThreadParticipant"
        securitySchemeNames("token")
        request {
            body<RemoveParticipantInput> {
                description = "Thread and participant information details"
            }
            agentParams()
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
            }
            HttpStatusCode.NotFound to {
                description = "Agent or thread not found"
                body<RouteException>()
            }
            HttpStatusCode.BadRequest to {
                description = "Participant cannot be removed"
                body<RouteException>()
            }
        }
    }) { path ->
        val agent = getAgent(path.parent.parent)
        val input = call.receive<RemoveParticipantInput>()

        try {
            val thread = agent.session.getThreadById(input.threadId)
            thread.removeParticipant(agent, agent.session.getAgent(input.participantName))

            call.respond(HttpStatusCode.OK)
        } catch (e: SessionException.MissingThreadException) {
            throw RouteException(HttpStatusCode.NotFound, e)
        } catch (e: SessionException.MissingAgentException) {
            throw RouteException(HttpStatusCode.NotFound, e)
        } catch (e: SessionException.AlreadyParticipatingException) {
            throw RouteException(HttpStatusCode.BadRequest, e)
        } catch (e: SessionException.NotParticipatingException) {
            throw RouteException(HttpStatusCode.BadRequest, e)
        }
    }

    delete<Puppet.Agent>({
        summary = "End agent runtime"
        description = "Forcefully cause an agent to exit it's own runtime"
        operationId = "puppetKillAgent"
        securitySchemeNames("token")
        request {
            agentParams()
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
            }
            HttpStatusCode.NotFound to {
                description = "Agent not found"
                body<RouteException>()
            }
            HttpStatusCode.BadRequest to {
                description = "Agent not running"
                body<RouteException>()
            }
        }
    }) { path ->
        val agent = getAgent(path)

        try {
            agent.session.cancelAndJoinAgent(agent.name)
            call.respond(HttpStatusCode.OK)
        } catch (e: SessionException.MissingAgentException) {
            throw RouteException(HttpStatusCode.NotFound, e)
        } catch (e: SessionException.NotLaunchedException) {
            throw RouteException(HttpStatusCode.BadRequest, e)
        }
    }
}
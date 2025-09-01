//package org.coralprotocol.coralserver.models
//
//import kotlinx.serialization.json.Json
//import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
//import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
//import org.coralprotocol.coralserver.agent.graph.GraphAgentServer
//import org.coralprotocol.coralserver.agent.graph.GraphAgentServerAttribute
//import org.coralprotocol.coralserver.agent.graph.GraphAgentServerAttributeType
//import org.coralprotocol.coralserver.agent.graph.GraphAgentServerCustomScorer
//import org.coralprotocol.coralserver.agent.graph.GraphAgentServerScorerEffect
//import org.coralprotocol.coralserver.agent.graph.GraphAgentServerScoring
//import org.coralprotocol.coralserver.agent.graph.GraphAgentServerSource
//import org.coralprotocol.coralserver.agent.runtime.RuntimeId
//
//fun local(json: Json): String =
//    json.encodeToString(GraphAgentRequest(
//        agentName = "interface",
//        options = mapOf(),
//        blocking = false,
//        tools = setOf(),
//        provider = GraphAgentProvider.Local(RuntimeId.DOCKER)
//    ))
//
//fun remote(json: Json): String =
//    json.encodeToString(GraphAgentRequest(
//        agentName = "interface",
//        options = mapOf(),
//        blocking = false,
//        tools = setOf(),
//        provider = GraphAgentProvider.Remote(
//            runtime = RuntimeId.DOCKER,
//            serverSource = GraphAgentServerSource.Servers(
//                listOf(
//                    GraphAgentServer("https://hackathon.coralprotocol.org:5555",
//                        listOf(
//                            GraphAgentServerAttribute.String(GraphAgentServerAttributeType.ATTESTED_BY, "Coral Team"),
//                        )),
//                    GraphAgentServer("https://coral.mycompany.com:5555",
//                        listOf(
//                            GraphAgentServerAttribute.String(GraphAgentServerAttributeType.ATTESTED_BY, "Myself"),
//                        ))
//                )
//            ),
//            serverScoring = GraphAgentServerScoring.Custom(
//                scorers = listOf(
//                    GraphAgentServerCustomScorer.StringEqual(
//                        type = GraphAgentServerAttributeType.ATTESTED_BY,
//                        string = "Coral Team",
//                        effect = GraphAgentServerScorerEffect.Flat(10.0)
//                    ),
//                    GraphAgentServerCustomScorer.StringEqual(
//                        type = GraphAgentServerAttributeType.ATTESTED_BY,
//                        string = "Myself",
//                        effect = GraphAgentServerScorerEffect.Flat(20.0)
//                    )
//                )
//            )
//        )
//    ))
//
//fun main() {
//    val json = Json {
//        encodeDefaults = true
//        prettyPrint = true
//        explicitNulls = false
//    }
//
//    println(local(json))
//    println(remote(json))
//}
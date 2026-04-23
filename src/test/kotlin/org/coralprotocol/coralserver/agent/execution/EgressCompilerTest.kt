package org.coralprotocol.coralserver.agent.execution

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.ktor.http.*

class EgressCompilerTest : FunSpec({

    val coralApi = Url("http://host.docker.internal:5555/")
    val coralMcp = Url("http://host.docker.internal:5555/mcp/v1/agent-secret/mcp")
    val coralLlm = Url("http://host.docker.internal:5555/llm-proxy/agent-secret")
    val coralUrls = setOf(coralApi, coralMcp, coralLlm)

    test("nullDeclarationStillEmitsCoralManagedEndpoints") {
        val policy = EgressCompiler.compile(declared = null, coralUrls = coralUrls)

        policy.declared.shouldContainExactlyInAnyOrder()
        policy.coralManaged.shouldContainExactlyInAnyOrder(
            EgressEndpoint("host.docker.internal", 5555),
        )
    }

    test("declaredHostsDefaultToHttpsPort") {
        val declared = ExecutionConfig(
            minIsolation = MinIsolation.CONTAINER,
            network = NetworkDeclaration(externalHosts = setOf("api.github.com", "api.firecrawl.dev")),
        )

        val policy = EgressCompiler.compile(declared = declared, coralUrls = coralUrls)

        policy.declared.shouldContainExactlyInAnyOrder(
            EgressEndpoint("api.github.com", 443),
            EgressEndpoint("api.firecrawl.dev", 443),
        )
    }

    test("allAllowedMergesDeclaredAndCoralManaged") {
        val declared = ExecutionConfig(
            minIsolation = MinIsolation.CONTAINER,
            network = NetworkDeclaration(externalHosts = setOf("api.github.com")),
        )

        val policy = EgressCompiler.compile(declared = declared, coralUrls = coralUrls)

        policy.allAllowed().shouldContainExactlyInAnyOrder(
            EgressEndpoint("api.github.com", 443),
            EgressEndpoint("host.docker.internal", 5555),
        )
    }
})

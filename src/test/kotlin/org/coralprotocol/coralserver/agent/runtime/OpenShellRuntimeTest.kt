package org.coralprotocol.coralserver.agent.runtime

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.coralprotocol.coralserver.agent.execution.EgressEndpoint
import org.coralprotocol.coralserver.agent.execution.EgressPolicy

class OpenShellRuntimeTest : FunSpec({

    test("renderedPolicyMatchesOpenShellExpectedSchema") {
        val policy = EgressPolicy(
            declared = setOf(
                EgressEndpoint("api.github.com", 443),
                EgressEndpoint("api.firecrawl.dev", 443),
            ),
            coralManaged = setOf(EgressEndpoint("host.docker.internal", 5555)),
        )

        val yaml = renderOpenShellPolicy(policy)

        yaml shouldBe """
            version: 1

            filesystem_policy:
              include_workdir: true
              read_only:
                - /usr
                - /lib
                - /lib64
                - /bin
                - /sbin
                - /etc
                - /proc
                - /dev/urandom
                - /var/log
              read_write:
                - /sandbox
                - /tmp

            landlock:
              compatibility: best_effort

            process:
              run_as_user: sandbox
              run_as_group: sandbox

            network_policies:
              coral_api:
                name: coral_api
                endpoints:
                  - host: host.docker.internal
                    port: 5555
                    allowed_ips: ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"]
                binaries:
                  - path: "/**"
              external_api_firecrawl_dev:
                name: external_api_firecrawl_dev
                endpoints:
                  - host: api.firecrawl.dev
                    port: 443
                binaries:
                  - path: "/**"
              external_api_github_com:
                name: external_api_github_com
                endpoints:
                  - host: api.github.com
                    port: 443
                binaries:
                  - path: "/**"

        """.trimIndent()
    }

    test("emptyEgressEmitsHeadersAndNoPolicies") {
        val yaml = renderOpenShellPolicy(EgressPolicy(declared = emptySet(), coralManaged = emptySet()))

        yaml shouldContain "version: 1"
        yaml shouldContain "filesystem_policy:"
        yaml shouldContain "landlock:"
        yaml shouldContain "process:"
        yaml shouldContain "network_policies:"
        yaml shouldNotContain "  coral_api:"
        yaml shouldNotContain "  external_"
    }

    test("policyNamesSanitiseNonAlphanumericCharacters") {
        val policy = EgressPolicy(
            declared = setOf(EgressEndpoint("host-with.dashes.example.com", 443)),
            coralManaged = emptySet(),
        )

        val yaml = renderOpenShellPolicy(policy)

        yaml shouldContain "  external_host_with_dashes_example_com:"
        yaml shouldContain "    name: external_host_with_dashes_example_com"
    }
})

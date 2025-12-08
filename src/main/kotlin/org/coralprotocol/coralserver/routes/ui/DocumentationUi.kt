package org.coralprotocol.coralserver.routes.ui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*

private val logger = KotlinLogging.logger {}

@Resource("docs")
class Documentation

fun Route.documentationInterface() {
    get<Documentation>({
        hidden = true
    }) {
        call.respondHtml(HttpStatusCode.OK) {
            head {
                title("Scalar API Reference")
                meta(charset = "utf-8")
                meta(name = "viewport", content = "width=device-width, initial-scale=1")
            }
            body {
                div {
                    id = "app"
                }

                // Load the Script
                script(src = "https://cdn.jsdelivr.net/npm/@scalar/api-reference") {}

                // Initialize the Scalar API Reference
                // this can accept multiple versions of the spec if we need it in the future
                script {
                    unsafe {
                        raw("""
                                Scalar.createApiReference('#app', {
                                  url: '/api_v1.json',
                                })
                                """.trimIndent())
                    }
                }
            }
        }
    }
}
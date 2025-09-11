package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@Resource("/api/v1/wallet/public")
class PublicWallet()

fun Routing.publicWalletApiRoutes(
    publicWalletAddress: String
) {
    get<PublicWallet>({
        summary = "Get public wallet address"
        description = "Retrieves the wallet address that this server users. This might be separate from the configured keypair."
        operationId = "getPublicWallet"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<String> {
                    description = "The wallet address"
                }
            }
        }
    }) {
        call.respond(HttpStatusCode.OK, publicWalletAddress)
    }
}

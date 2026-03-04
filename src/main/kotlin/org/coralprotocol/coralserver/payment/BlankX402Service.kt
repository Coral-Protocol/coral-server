package org.coralprotocol.coralserver.payment

import org.coralprotocol.payment.blockchain.X402Service
import org.coralprotocol.payment.blockchain.models.X402PaymentResult

class BlankX402Service : X402Service {
    override suspend fun executeX402Payment(
        serviceUrl: String,
        method: String,
        body: String?,
        headers: Map<String, String>
    ): Result<X402PaymentResult> {
        return Result.failure(NotImplementedError())
    }
}
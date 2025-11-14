package org.coralprotocol.coralserver.x402

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class X402PaymentRequirement(
    @Description("Scheme of the payment protocol to use")
    val scheme: String,

    @Description("Network of the blockchain to send payment on")
    val network: String,

    /**
     * uint256 as a string
     */
    @Description("Maximum amount required to pay for the resource in atomic units of the asset")
    val maxAmountRequired: String,

    @Description("URL of resource to pay for")
    val resource: String,

    @Description("Description of the resource")
    val description: String,

    @Description("MIME type of the resource response")
    val mimeType: String,

    @Description("Output schema of the resource response")
    val outputSchema: JsonObject? = null,

    @Description("Address to pay value to")
    val payTo: String,

    @Description("Maximum time in seconds for the resource server to respond")
    val maxTimeoutSeconds: Double,

    @Description("Address of the EIP-3009 compliant ERC20 contract")
    val asset: Double,

    @Description("""
        Extra information about the payment details specific to the scheme
        For `exact` scheme on a EVM network, expects extra to contain the records `name` and `version` pertaining to asset
    """)
    val extra: JsonObject? = null,
)

fun X402PaymentRequirement.withinBudget(budgetedResource: X402BudgetedResource): Boolean {
    if (budgetedResource.resource != resource)
        return false

    return maxAmountRequired.toBigInteger() <= budgetedResource.remainingBudget.toString().toBigInteger()
}
package org.coralprotocol.coralserver.models

import kotlinx.serialization.Serializable

@Serializable
data class OpenAiModel(
    val id: String,
    val `object`: String = "model",
    val created: Long = 0,
    val owned_by: String = "coral"
)

@Serializable
data class OpenAiModelList(
    val `object`: String = "list",
    val data: List<OpenAiModel>
)

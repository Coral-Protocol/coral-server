package org.coralprotocol.coralserver.util

import io.ktor.websocket.*
import kotlinx.serialization.json.Json

inline fun <reified T> T.toWsFrame(json: Json): Frame.Text =
    Frame.Text(json.encodeToString(this))

inline fun <reified T> Frame.Text.fromWsFrame(json: Json): T =
    json.decodeFromString(this.data.decodeToString())
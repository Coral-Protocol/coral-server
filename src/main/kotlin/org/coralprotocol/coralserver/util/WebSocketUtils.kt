package org.coralprotocol.coralserver.util

import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import org.coralprotocol.coralserver.server.apiJsonConfig

inline fun <reified T> T.toWsFrame(): Frame.Text =
    Frame.Text(apiJsonConfig.encodeToString(this))

inline fun <reified T> Frame.Text.fromWsFrame(): T =
    apiJsonConfig.decodeFromString(this.data.decodeToString())
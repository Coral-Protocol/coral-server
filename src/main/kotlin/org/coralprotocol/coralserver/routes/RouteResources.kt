package org.coralprotocol.coralserver.routes

import io.ktor.resources.Resource

@Resource("api/v1")
class ApiV1

@Resource("sse/v1")
class SseV1

@Resource("ws/v1")
class WsV1

@Resource("ui")
class UiV1
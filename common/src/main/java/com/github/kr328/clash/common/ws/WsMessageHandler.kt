package com.github.kr328.clash.common.ws

fun interface WsMessageHandler {
    fun onMessage(operation: WebsocketOperation): WebsocketResult
}
package com.github.kr328.clash.common.ws

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket

object WsServerContext {

    lateinit var nanoWSD: NanoWSD

    private val TAG = WsServerContext::class.java.simpleName

    lateinit var hostIp: String

    private var _serverPort: Int = 0

    val serverPort: Int
        get() = _serverPort

    private lateinit var htmlContent: String
    private val gson = Gson()

    var messageHandler: WsMessageHandler? = null

    fun init(context: Context) {
        hostIp = findIp()
        htmlContent = context.assets.open("input.html").readBytes().toString(Charsets.UTF_8)
        initHttpServer()
    }

    fun registerHandler(handler: WsMessageHandler) {
        Log.d(TAG, "registerHandler: $handler")
        messageHandler = handler
    }

    fun removeHandler(handler: WsMessageHandler?) {
        Log.d(TAG, "removeHandler")
        if (messageHandler == handler) {
            messageHandler = null
        }
    }

    @Keep
    private data class WebsocketResponse(val success: Boolean, val message: String = "")


    private fun findPort(): Int? {
        for (port in 12345..50000) {
            try {
                ServerSocket(port).apply {
                    try {
                        close()
                    } catch (_: Exception) {
                    }
                }
                return port
            } catch (_: Exception) {

            }
        }
        return null
    }

    private fun findIp(): String {
        var candidate: InetAddress? = null
        val result = NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .find {
                if (candidate == null && !it.isLoopbackAddress) {
                    candidate = it
                }
                it.isSiteLocalAddress && it.hostAddress != null && Regex("\\d+\\.\\d+\\.\\d+\\.\\d+").matches(
                    it.hostAddress!!
                )
            }
        return (result ?: candidate ?: InetAddress.getLocalHost()).hostAddress!!
    }

    private fun initHttpServer() {
        _serverPort = findPort()!!
        nanoWSD = object : NanoWSD(_serverPort) {
            override fun openWebSocket(handshake: IHTTPSession): WebSocket =
                object : WebSocket(handshake) {
                    override fun onOpen() {
                    }

                    override fun onClose(
                        code: WebSocketFrame.CloseCode?,
                        reason: String?,
                        initiatedByRemote: Boolean
                    ) {
                    }

                    override fun onMessage(message: WebSocketFrame) {
                        try {
                            handleIncomingMessage(message)
                        } catch (ex: Exception) {
                            Log.e(TAG, "handle ws message error: ${ex.message}", ex)
                            close(WebSocketFrame.CloseCode.InternalServerError, ex.message, false)
                        }
                    }

                    override fun onPong(pong: WebSocketFrame?) {
                    }

                    override fun onException(exception: IOException) {
                        Log.e(TAG, "websocket io error:${exception.message}", exception)
                        close(
                            WebSocketFrame.CloseCode.InternalServerError,
                            exception.message,
                            false
                        )
                    }

                }

            override fun serveHttp(session: IHTTPSession): Response {
                return NanoHTTPD.newFixedLengthResponse(
                    Response.Status.OK,
                    NanoHTTPD.MIME_HTML,
                    htmlContent
                )
            }

        }
        nanoWSD.start(Int.MAX_VALUE, true)
    }

    private fun WebSocket.handleIncomingMessage(wsFrame: WebSocketFrame) {
        if (messageHandler == null) {
            sendJson(WebsocketResponse(false, "无监听输入框"))
            return
        }
        val payload = wsFrame.textPayload
        Log.d(TAG, "handleIncomingMessage: receive message $payload")
        val message = gson.fromJson(payload, WebsocketIncomingMessage::class.java)
        Log.d(TAG, "handleIncomingMessage: receive message obj:$message")
        val operation = when (message.operation.uppercase()) {
            "INPUT" -> WebsocketOperation.Input(message.content)
            "SUBMIT" -> WebsocketOperation.Submit
            else -> throw IllegalArgumentException("invalid operation${message.operation}")
        }
        runBlocking {
            val result = withContext(Dispatchers.Main) {
                Log.d(
                    TAG,
                    "handleIncomingMessage: message handler is ${if (messageHandler == null) "" else "not"} null"
                )
                messageHandler!!.onMessage(operation)
            }
            Log.d(TAG, "handleIncomingMessage: handle result is $result")
            when (result) {
                is WebsocketResult.Success -> sendJson(
                    WebsocketResponse(
                        true
                    )
                )
                is WebsocketResult.Error -> sendJson(
                    WebsocketResponse(
                        false,
                        result.message
                    )
                )
                is WebsocketResult.Close -> close(
                    WebSocketFrame.CloseCode.NormalClosure,
                    result.message,
                    false
                )
            }
        }
    }

    private fun WebSocket.sendJson(data: Any) {
        send(gson.toJson(data))
    }

    fun stopServer() {
        nanoWSD.stop()
    }
}
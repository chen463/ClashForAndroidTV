package com.github.kr328.clash.common.ws

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class WebsocketIncomingMessage(
    @SerializedName("operation")
    val operation: String,
    @SerializedName("content")
    val content: String = ""
)

sealed class WebsocketOperation {
    data class Input(val data: String) : WebsocketOperation()
    object Submit : WebsocketOperation()
}
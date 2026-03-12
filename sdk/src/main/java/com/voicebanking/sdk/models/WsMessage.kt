package com.voicebanking.sdk.models

import com.google.gson.annotations.SerializedName

data class WsMessage(
    @SerializedName("request_id")
    val requestId: String,

    @SerializedName("type")
    val type: String,

    @SerializedName("payload")
    val payload: Payload
)

data class Payload(
    @SerializedName("message")
    val message: String
)
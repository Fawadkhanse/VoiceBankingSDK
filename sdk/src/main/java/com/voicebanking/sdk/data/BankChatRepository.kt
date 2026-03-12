package com.voicebanking.sdk.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.voicebanking.sdk.models.ActionStatusMessage
import com.voicebanking.sdk.models.BeneficiaryPayload
import com.voicebanking.sdk.models.ClientResponseMessage
import com.voicebanking.sdk.models.InternalActionPayload
import com.voicebanking.sdk.models.InternalChatEvent
import com.voicebanking.sdk.models.InternalServerMessage
import com.voicebanking.sdk.models.SdkBeneficiary
import com.voicebanking.sdk.models.UserChatMessage
import com.voicebanking.sdk.models.WsMessage
import com.voicebanking.sdk.utils.SdkLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class BankChatRepository(
    private val baseUrl:      String,
    enableLogging:            Boolean = false
) {
    private val SUB = "WS"          // subtag shown inside [ ] in every log line
    private val gson = Gson()

    private val httpClient: OkHttpClient

    init {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (enableLogging) {
            builder.addInterceptor(HttpLoggingInterceptor { msg ->
                SdkLogger.d("HTTP", msg)
            }.apply { level = HttpLoggingInterceptor.Level.BODY })
        }
        httpClient = builder.build()
    }

    private var webSocket: WebSocket?  = null
    private var sessionId: String?     = null

    private val _events = MutableSharedFlow<InternalChatEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<InternalChatEvent> = _events

    // ── Session ───────────────────────────────────────────────────────────────

    fun startSession(): String {
        val wsBase  = baseUrl.replace("ws://", "http://").replace("wss://", "https://")
        val request = Request.Builder()
            .url("$wsBase/start-session")
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()

        val response = httpClient.newCall(request).execute()
        val body     = response.body?.string() ?: throw RuntimeException("Empty body from /start-session")
        val id       = JSONObject(body).getString("session_id")
        SdkLogger.d(SUB, "startSession → $body")
        sessionId    = id
        SdkLogger.d(SUB, "session_id=$id")
        return id
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────

    fun connectWebSocket(sid: String) {
        disconnect()
        val wsBase = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        val url    = "$wsBase/chat/$sid"
        SdkLogger.d(SUB, "CONNECT → $url")

        val req = Request.Builder().url(url).build()
        webSocket = httpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                SdkLogger.d(SUB, "OPEN")
                _events.tryEmit(InternalChatEvent.Connected)
            }
            override fun onMessage(ws: WebSocket, text: String) {
                SdkLogger.d(SUB, "RECV ← $text")
                handleIncoming(text)
            }
            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                SdkLogger.d(SUB, "CLOSING code=$code reason=$reason")
                ws.close(1000, null)
                _events.tryEmit(InternalChatEvent.Disconnected(reason))
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                SdkLogger.e(SUB, "FAILURE: ${t.message}", t)
                _events.tryEmit(InternalChatEvent.Error(t.message ?: "WebSocket failure"))
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "client disconnect")
        webSocket = null
    }

    // ── Send helpers ──────────────────────────────────────────────────────────

    fun sendUserMessage(text: String): String {
        val requestId = UUID.randomUUID().toString()
        val msg  = UserChatMessage(message = text, request_id = requestId)
        val json = gson.toJson(msg)
        SdkLogger.d(SUB, "SEND userMessage → $json")
        webSocket?.send(json)
        return requestId
    }

    fun sendBeneficiaryList(requestId: String, beneficiaries: List<SdkBeneficiary>) {
        val resp = ClientResponseMessage(
            type       = "client_response",
            request_id = requestId,
            action     = "get_beneficiary_list",
            payload    = BeneficiaryPayload(beneficiaries)
        )
        val json = gson.toJson(resp)
        SdkLogger.d(SUB, "SEND beneficiaryList → $json")
        webSocket?.send(json)
    }

    fun sendActionStatus(
        requestId:   String,
        actionId:    String,
        serviceName: String,
        status:      String,
        message:     String = ""
    ) {
        val msg = ActionStatusMessage(
            type        = "action_status",
            request_id  = requestId,
            action_id   = actionId,
            serviceName = serviceName,
            status      = status,
            message     = message
        )
        val json = gson.toJson(msg)
        SdkLogger.d(SUB, "SEND actionStatus[$status] → $json")
        webSocket?.send(json)
    }

    // ── Incoming parser ───────────────────────────────────────────────────────

    private fun handleIncoming(raw: String) {
        if (raw.isBlank() || raw.trim().all { it.isDigit() }) {
            SdkLogger.d(SUB, "Skipping non-JSON frame: $raw")
            return
        }
        try {
            val el = JsonParser.parseString(raw)

            // ── JSON array: list of typed objects ─────────────────────────────
            if (el.isJsonArray) {
                var actionPayload: InternalActionPayload? = null
                var messageText: String? = null

                el.asJsonArray.forEach { item ->
                    val obj = item.asJsonObject
                    when (obj.get("type")?.asString) {
                        "action"  -> actionPayload = gson.fromJson(obj, InternalServerMessage.Action::class.java).action
                        "message" -> messageText   = obj.get("message")?.asString
                    }
                }
                actionPayload?.let {
                  //  SdkLogger.d(SUB, "Parsed action from array: ${it.serviceName}")
                    _events.tryEmit(InternalChatEvent.ActionReceived(it))
                }
                messageText?.let {
                //    SdkLogger.d(SUB, "Parsed message from array: $it")
                    _events.tryEmit(InternalChatEvent.MessageReceived(it))
                }
                return
            }

            val obj      = el.asJsonObject
            val rootType = obj.get("type")?.asString
            SdkLogger.d(SUB, "Incoming type=$rootType")

            // ── server_request (e.g. get_beneficiary_list) ────────────────────
            if (rootType == "server_request") {
                val action    = obj.get("action")?.asString ?: return
                val requestId = obj.get("request_id")?.asString ?: UUID.randomUUID().toString()
           //     SdkLogger.d(SUB, "server_request action=$action requestId=$requestId")
                if (action == "get_beneficiary_list") {
                    _events.tryEmit(InternalChatEvent.BeneficiaryListRequested(requestId))
                }
                return
            }

            // ── action_bundle: { type, payload: { action, message } } ─────────
            // Observed in logs: "type":"action_bundle","payload":{"action":{…},"message":"…"}
            if (rootType == "action_bundle") {
                val payloadObj = obj.get("payload")?.asJsonObject
                if (payloadObj != null) {
                    val actionObj = payloadObj.get("action")?.asJsonObject
                    val msgText   = payloadObj.get("message")?.asString

                    if (actionObj != null) {
                        val actionPayload = gson.fromJson(actionObj, InternalActionPayload::class.java)
                        SdkLogger.d(SUB, "action_bundle → action=${actionPayload.serviceName} msg=$msgText")
                        _events.tryEmit(InternalChatEvent.ActionReceived(actionPayload))
                    }
                    if (!msgText.isNullOrEmpty()) {
                        _events.tryEmit(InternalChatEvent.MessageReceived(msgText))
                    }
                }
                return
            }

            // ── flat message/action (no payload wrapper) ──────────────────────
            if (obj.get("payload") == null) {
                when (rootType) {
                    "message" -> {
                        val text = obj.get("message")?.asString ?: return
                      //  SdkLogger.d(SUB, "flat message: $text")
                        _events.tryEmit(InternalChatEvent.MessageReceived(text))
                    }
                    "action" -> {
                        val action = gson.fromJson(obj, InternalServerMessage.Action::class.java).action
                //        SdkLogger.d(SUB, "flat action: ${action.serviceName}")
                        _events.tryEmit(InternalChatEvent.ActionReceived(action))
                    }
                }
                return
            }

            // ── wrapped payload ───────────────────────────────────────────────
            val payload = obj.get("payload")

            if (payload.isJsonArray) {
              //  SdkLogger.d(SUB, "payload is array — recursing")
                handleIncoming(payload.toString())
                return
            }

            val payloadObj = payload.asJsonObject

            when (rootType) {
                "message" -> {
                    val text = payloadObj.get("message")?.asString ?: return
                  //  SdkLogger.d(SUB, "wrapped message: $text")
                    _events.tryEmit(InternalChatEvent.MessageReceived(text))
                }
                "action" -> {
                    val action = gson.fromJson(payloadObj, InternalServerMessage.Action::class.java).action
                  //  SdkLogger.d(SUB, "wrapped action: ${action.serviceName}")
                    _events.tryEmit(InternalChatEvent.ActionReceived(action))
                }
                else -> SdkLogger.w(SUB, "Unhandled type=$rootType payload=$payloadObj")
            }

        } catch (e: Exception) {
            SdkLogger.e(SUB, "Parse error: ${e.message} | raw=$raw", e)
        }
    }
}
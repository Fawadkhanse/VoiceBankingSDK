package com.voicebanking.sdk.models

import com.google.gson.annotations.SerializedName

// ─────────────────────────────────────────────────────────────────────────────
// Public SDK configuration
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Configuration used to initialise [VoiceBankingSDK].
 *
 * All parameters have built-in defaults matching the production server,
 * so the SDK can be initialised with just:
 *
 *     VoiceBankingSDK.getInstance().init(context)
 *
 * Override any field to point at a different server or use different credentials.
 */
data class VoiceBankingConfig(
    val chatApiUrl:      String  = SdkDefaults.CHAT_API_URL,
    val googleApiKey:    String  = SdkDefaults.GOOGLE_API_KEY,
    val gcpProjectId:    String  = SdkDefaults.GCP_PROJECT_ID,
    val gcpClientEmail:  String  = SdkDefaults.GCP_CLIENT_EMAIL,
    val gcpPrivateKey:   String  = SdkDefaults.GCP_PRIVATE_KEY,
    val language:        String  = "urdu",
    val sttModel:        String  = "long",
    val ttsVoiceName:    String  = "ur-IN-Standard-B",
    val ttsLanguageCode: String  = "ur-IN",
    val enableLogging:   Boolean = false
)

/** Pre-configured credentials bundled with the SDK. */
internal object SdkDefaults {
    const val CHAT_API_URL    = "http://59.103.233.98:5012"
    const val GOOGLE_API_KEY  = "AIzaSyDFRvy-Vit68NSFobCLFQ89_zXJo2w4Xmc"
    const val GCP_PROJECT_ID  = "hale-carport-488819-q4"
    const val GCP_CLIENT_EMAIL =
        "speech-v2@hale-carport-488819-q4.iam.gserviceaccount.com"
    const val GCP_PRIVATE_KEY = """-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDarMVGK4BH+Srw
ktiR6tZmpQuKxdTT6+8iLaJiJiyDiJK82yU5lFtk2qjul2d/CyCDX+QZ56CdOY2Q
lSTfU4vePz7hZ+ojRh01X8gYHcn4KAt2CxFt968xglnjcSmuFhtiPoADLDUThRkT
9CG1IhFf0IfP0R5h2rhdUWhaH8zYcvZWLfnDKhWYMXcEekjKyYx0+a5Ra/KFQOLw
kFpZpPpRgfD50Ju7eJ0A2dAESdhY1j4IgEzUki0fPj1jRdbb1otkNuC2d6WwlB2a
S9cfwjHwR/CXixrJ1eqb7WkNjP5Wt577p3XwZmap1opmImI6u379NyiJ8/1cTVRD
P58o+uXdAgMBAAECggEALIblJZhqht1K+9Ee9blonl6JluCZKWzflOlPaSZmRoNL
A9cowYx+vq7SmsCsI5+fp3ihYQ/78b/cHl5sM2hW8PKWan4HHFckz+84toZsT5JR
4R+4VRjL7OwiwxASIV9hhJP+64Z3KUofFOPPcEtnV8gFu5CUm+7WPzQ6KC3U/5aq
jdABo6WOo8bWyhnrkTD3M/YWsOORArDQHhqvU8NdUQ3T7dJhHG+vLjMqQGScGp/r
sN8doczruofppjTiYhGsYOIfoGsbd7+P7D5GWc6WfOQYEA6/26/uUDm8WBPAf91B
zgsoa8qPLfhYQ4ViOQNhFJ72BhJYGg0g49OLvwZg8QKBgQD+Gq2xEQ3YAP0Plft2
spW+mdCLOGUJ7QnJQmVs5HhIGbsOTorxvjjC5jzbpFLX4EH41oMV+2lMyIUNSLME
T44NIzJHAI9Il2QzrPeThwpa2ITPZpQVeLIpfCJC8tLKynxaXr+TKBhZHE4CvHwC
b0LYPyfRr8BR5cPd1PVUPbA08QKBgQDcTmyvH52wzjp4NXQs5J80lKt7VUIrJuCU
zpFXJBnj0sg+NJPS8Kx4z9fh9lozPZqF24SgUP5f4vAvCqfCdWpPXkShVdJ/H4vC
7jtWhj2gJsbPGhKrs7zZUQSFUiRd2GeRuaEOVb2wAOwLh0KxEWhpH/F8oj7yRsK6
ChYGjp0PrQKBgA4k7gYtLNgZNfzoHFc/GZbCeRlGylkDGMhbKcol7YwV4pOpS5Kp
Q/+VUU3ol7Psh7+SMTnIBNSBVOaoZU6YHxAcJXBOV6tyweEef6l2mtzzsHDbBOMt
FL26ay3O1mzzWHivTXqjgLd1G+KLG1wHVXE0EsNZRRtJ7t0qPX2y8VwBAoGAYpdS
OjkK6AIS1pMNb73MpcpWx7YLC6a1YMLk9jt4vqUo6fW7pe4BMXvKYBxQl5fdHER2
IQy+GglEdbjuBK9pKSXFzvHKZwumD1FwCrO+xno0BKDldCPWwuZoAIYXMkxTZTrO
ocyrPCXdfPdGWFmzAUDDYIR3aRNTt9AltT+DeG0CgYEAzGIBSlU2qFJCkQeL3QaY
ozBHeoG8k5LkqvljxZ9N7/EAxR8rYdHAZzYCfEuXllvWXe4kuWURliGfQRDv8yzo
QdrHQM3SEPqr+0k98ZUl++JOe1T3pQP2nfq6NYo+Tq+fzHk+P3KtPTPkAEVblhep
UOa35pOe/mT75TxugNsG7A8=
-----END PRIVATE KEY-----"""
}

// ─────────────────────────────────────────────────────────────────────────────
// Public SDK events – emitted on the [VoiceBankingSDK.events] LiveData
// ─────────────────────────────────────────────────────────────────────────────

sealed class SdkEvent {
    /** WebSocket connected and session ready. */
    object Connected : SdkEvent()

    /** WebSocket disconnected. */
    data class Disconnected(val reason: String) : SdkEvent()

    /** Any non-fatal error (STT failure, TTS failure, parse error …). */
    data class Error(val message: String) : SdkEvent()

    /** Microphone is active and recording. */
    object RecordingStarted : SdkEvent()

    /** Recording finished; STT in progress. */
    object RecordingStopped : SdkEvent()

    /** STT produced a transcript from user speech. */
    data class TranscriptReady(val text: String) : SdkEvent()

    /** Bot text response arrived from the server. */
    data class BotMessageReceived(val text: String) : SdkEvent()

    /** TTS playback started. */
    object PlaybackStarted : SdkEvent()

    /** TTS playback finished. */
    object PlaybackFinished : SdkEvent()

    /** Server wants an action performed; host app must call [VoiceBankingSDK.confirmAction] or [cancelAction]. */
    data class ActionRequired(val action: SdkAction) : SdkEvent()

    /** Server requests the beneficiary list; host app must call [VoiceBankingSDK.sendBeneficiaryList]. */
    data class BeneficiaryListRequested(val requestId: String) : SdkEvent()
}

// ─────────────────────────────────────────────────────────────────────────────
// Action / beneficiary models
// ─────────────────────────────────────────────────────────────────────────────

data class SdkAction(
    val serviceName: String,
    val actionId:    String,
    val parameters:  Map<String, Any?> = emptyMap(),
    val requestId:   String = ""
)

data class SdkBeneficiary(
    @SerializedName("beneficiary_name")
    val beneficiaryName: String,
    @SerializedName("bank_name")
    val bankName:        String
)

// ─────────────────────────────────────────────────────────────────────────────
// Internal WebSocket / HTTP models (kept internal to the SDK)
// ─────────────────────────────────────────────────────────────────────────────

internal data class UserChatMessage(
    val message:    String,
    val request_id: String
)


internal data class ClientResponseMessage(
    val type:       String,
    val request_id: String,
    val action:     String,
    val payload:    BeneficiaryPayload
)
internal data class BeneficiaryPayload(
    val beneficiaries:List<SdkBeneficiary>
 )


internal data class ActionStatusMessage(
    val type:        String,
    val request_id:  String,
    val action_id:   String,
    val serviceName: String,
    val status:      String,
    val message:     String
)

internal sealed class InternalServerMessage {
    data class Action(val type: String?, val action: InternalActionPayload) : InternalServerMessage()
    data class Message(val type: String?, val message: String) : InternalServerMessage()
}

internal data class InternalActionPayload(
    val serviceName: String,
    val action_id:   String,
    val parameters:  Map<String, Any?>?
)

internal sealed class InternalChatEvent {
    object Connected : InternalChatEvent()
    data class Disconnected(val reason: String) : InternalChatEvent()
    data class Error(val message: String) : InternalChatEvent()
    data class MessageReceived(val text: String) : InternalChatEvent()
    data class ActionReceived(val action: InternalActionPayload) : InternalChatEvent()
    data class BeneficiaryListRequested(val requestId: String) : InternalChatEvent()
}

// ─────────────────────────────────────────────────────────────────────────────
// Google Cloud STT v2 models
// ─────────────────────────────────────────────────────────────────────────────

internal data class SpeechV2Request(
    val recognizer: String,
    val config:     SpeechV2Config,
    val content:    String
)

internal data class SpeechV2Config(
    val explicitDecodingConfig: ExplicitDecodingConfig,
    val languageCodes:          List<String>,
    val model:                  String
)

internal data class ExplicitDecodingConfig(
    val encoding:          String,
    val sampleRateHertz:   Int,
    val audioChannelCount: Int
)

internal data class SpeechV2Response(
    val results: List<SpeechV2Result>?
)

internal data class SpeechV2Result(
    val alternatives: List<SpeechV2Alternative>?,
    val languageCode: String?
)

internal data class SpeechV2Alternative(
    val transcript: String?,
    val confidence: Float?
)

// ─────────────────────────────────────────────────────────────────────────────
// Google Cloud TTS v1 models
// ─────────────────────────────────────────────────────────────────────────────

internal data class TtsSynthesizeRequest(
    val input:       TtsInput,
    val voice:       TtsVoice,
    val audioConfig: TtsAudioConfig
)

internal data class TtsInput(val text: String)

internal data class TtsVoice(
    val languageCode: String,
    val name:         String
)

internal data class TtsAudioConfig(
    val audioEncoding:   String = "LINEAR16",
    val sampleRateHertz: Int    = 24000
)

internal data class TtsSynthesizeResponse(
    val audioContent: String?
)
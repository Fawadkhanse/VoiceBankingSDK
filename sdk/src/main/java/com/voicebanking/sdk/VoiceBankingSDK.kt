package com.voicebanking.sdk

import android.content.Context
import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.voicebanking.sdk.data.BankChatRepository
import com.voicebanking.sdk.data.ServiceAccountAuth
import com.voicebanking.sdk.data.SpeechV2Client
import com.voicebanking.sdk.data.TtsClient
import com.voicebanking.sdk.helper.AudioRecorder
import com.voicebanking.sdk.helper.PcmPlayer
import com.voicebanking.sdk.models.ExplicitDecodingConfig
import com.voicebanking.sdk.models.InternalChatEvent
import com.voicebanking.sdk.models.SdkAction
import com.voicebanking.sdk.models.SdkBeneficiary
import com.voicebanking.sdk.models.SdkEvent
import com.voicebanking.sdk.models.SpeechV2Config
import com.voicebanking.sdk.models.SpeechV2Request
import com.voicebanking.sdk.models.TtsAudioConfig
import com.voicebanking.sdk.models.TtsInput
import com.voicebanking.sdk.models.TtsSynthesizeRequest
import com.voicebanking.sdk.models.TtsVoice
import com.voicebanking.sdk.models.VoiceBankingConfig
import com.voicebanking.sdk.utils.SdkLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.util.UUID

/**
 * # VoiceBankingSDK
 *
 * Single entry-point for voice-based banking interactions.
 *
 * ## Logging
 * All SDK log lines use the tag **"VoiceSDK"**.
 * Enable verbose logging by setting [VoiceBankingConfig.enableLogging] = true.
 * Filter in Logcat:  Tag = VoiceSDK
 *
 * ## Quick start
 * ```kotlin
 * val sdk = VoiceBankingSDK.getInstance()
 * sdk.events.observe(viewLifecycleOwner) { event -> … }
 * sdk.init(requireContext())   // uses bundled defaults
 * ```
 */
class VoiceBankingSDK private constructor() {

    companion object {
        private const val SUB = "SDK"

        @Volatile private var INSTANCE: VoiceBankingSDK? = null

        /** Returns the singleton SDK instance. */
        @JvmStatic
        fun getInstance(): VoiceBankingSDK =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceBankingSDK().also { INSTANCE = it }
            }
    }

    // ── Public LiveData ───────────────────────────────────────────────────────

    private val _events = MutableLiveData<SdkEvent>()
    val events: LiveData<SdkEvent> get() = _events

    // ── Internal state ────────────────────────────────────────────────────────

    private var config:    VoiceBankingConfig? = null
    private var scope:     CoroutineScope?     = null
    private var repo:      BankChatRepository? = null
    private var sttClient: SpeechV2Client?     = null
    private var ttsClient: TtsClient?          = null
    private var auth:      ServiceAccountAuth? = null

    private val recorder  = AudioRecorder()
    private val player    = PcmPlayer()

    private var pendingRequestId: String? = null
    private var didStartRecording = false

    // ── Init ──────────────────────────────────────────────────────────────────

    /**
     * Initialise with bundled defaults.
     * Logging is OFF by default. To enable, pass a config:
     * ```kotlin
     * sdk.init(context, VoiceBankingConfig(enableLogging = true))
     * ```
     */
    fun init(context: Context) = init(context, VoiceBankingConfig())

    fun init(context: Context, config: VoiceBankingConfig) {
        if (this.config != null) {
            SdkLogger.d(SUB, "Already initialised — skipping")
            return
        }
        // Enable/disable the logger FIRST so every subsequent call is covered
        SdkLogger.enabled = config.enableLogging

        this.config = config
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        SdkLogger.i(SUB, "━━━ VoiceBankingSDK init ━━━")
        SdkLogger.i(SUB, "chatApiUrl    = ${config.chatApiUrl}")
        SdkLogger.i(SUB, "gcpProjectId  = ${config.gcpProjectId}")
        SdkLogger.i(SUB, "language      = ${config.language}")
        SdkLogger.i(SUB, "sttModel      = ${config.sttModel}")
        SdkLogger.i(SUB, "ttsVoiceName  = ${config.ttsVoiceName}")
        SdkLogger.i(SUB, "enableLogging = ${config.enableLogging}")
        SdkLogger.i(SUB, "━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        auth      = ServiceAccountAuth(config.gcpClientEmail, config.gcpPrivateKey)
        sttClient = SpeechV2Client(config.gcpProjectId, auth!!, config.enableLogging)
        ttsClient = TtsClient(config.googleApiKey, config.enableLogging)
        repo      = BankChatRepository(config.chatApiUrl, config.enableLogging)

        observeRepoEvents()

        scope!!.launch(Dispatchers.IO) {
            try {
                val sid = repo!!.startSession()
                repo!!.connectWebSocket(sid)
            } catch (e: Exception) {
                SdkLogger.e(SUB, "Init error: ${e.message}", e)
                emit(SdkEvent.Error("Connection failed: ${e.message}"))
            }
        }
    }

    fun isInitialized(): Boolean = config != null

    // ── Recording ─────────────────────────────────────────────────────────────

    fun startRecording() {
        player.stop()
        didStartRecording = true
        SdkLogger.d(SUB, "startRecording")
        emit(SdkEvent.RecordingStarted)
        recorder.startRecording()
    }

    fun stopAndProcess() {
        if (!didStartRecording) return
        didStartRecording = false
        SdkLogger.d(SUB, "stopAndProcess")
        emit(SdkEvent.RecordingStopped)

        scope?.launch {
            val base64Audio = withContext(Dispatchers.IO) { recorder.stopRecording() }
            if (base64Audio.isEmpty()) {
                SdkLogger.w(SUB, "No audio captured")
                emit(SdkEvent.Error("No audio captured"))
                return@launch
            }

            SdkLogger.d(SUB, "Audio captured — running STT")
            val transcript = runStt(base64Audio)
            if (transcript.startsWith("❌") || transcript.startsWith("🔇")) {
                SdkLogger.w(SUB, "STT result: $transcript")
                emit(SdkEvent.Error(transcript))
                return@launch
            }

            SdkLogger.d(SUB, "Transcript: $transcript")
            emit(SdkEvent.TranscriptReady(transcript))
            repo?.sendUserMessage(transcript)
                ?: emit(SdkEvent.Error("SDK not initialised"))
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun confirmAction(requestId: String, action: SdkAction) {
        SdkLogger.d(SUB, "confirmAction requestId=$requestId service=${action.serviceName}")
        repo?.sendActionStatus(
            requestId   = requestId,
            actionId    = action.actionId,
            serviceName = action.serviceName,
            status      = "confirmed",
            message     = "User confirmed"
        )
    }

    fun cancelAction(requestId: String, action: SdkAction) {
        SdkLogger.d(SUB, "cancelAction requestId=$requestId service=${action.serviceName}")
        repo?.sendActionStatus(
            requestId   = requestId,
            actionId    = action.actionId,
            serviceName = action.serviceName,
            status      = "canceled",
            message     = "User canceled"
        )
    }

    // ── Beneficiary list ──────────────────────────────────────────────────────

    fun sendBeneficiaryList(requestId: String, beneficiaries: List<SdkBeneficiary>) {
        SdkLogger.d(SUB, "sendBeneficiaryList requestId=$requestId count=${beneficiaries.size}")
        repo?.sendBeneficiaryList(requestId, beneficiaries)
    }

    // ── TTS ───────────────────────────────────────────────────────────────────

    fun speak(text: String) {
        val cfg = config ?: run { emit(SdkEvent.Error("SDK not initialised")); return }
        SdkLogger.d(SUB, "speak: $text")
        scope?.launch {
            try {
                emit(SdkEvent.PlaybackStarted)
                val request = TtsSynthesizeRequest(
                    input       = TtsInput(text),
                    voice       = TtsVoice(cfg.ttsLanguageCode, cfg.ttsVoiceName),
                    audioConfig = TtsAudioConfig("LINEAR16", 24000)
                )
                val response = withContext(Dispatchers.IO) { ttsClient!!.synthesize(request) }
                if (response.isSuccessful && !response.body()?.audioContent.isNullOrEmpty()) {
                    val pcm = Base64.decode(response.body()!!.audioContent!!, Base64.DEFAULT)
                    SdkLogger.d(SUB, "TTS OK — playing ${pcm.size} bytes")
                    player.play(pcm, 24000)
                    emit(SdkEvent.PlaybackFinished)
                } else {
                    SdkLogger.e(SUB, "TTS error ${response.code()}: ${response.errorBody()?.string()}")
                    emit(SdkEvent.Error("TTS error ${response.code()}"))
                }
            } catch (e: Exception) {
                SdkLogger.e(SUB, "TTS exception: ${e.message}", e)
                emit(SdkEvent.Error("TTS exception: ${e.message}"))
            }
        }
    }

    fun stopPlayback() {
        SdkLogger.d(SUB, "stopPlayback")
        player.stop()
    }

    // ── Voice / model helpers ─────────────────────────────────────────────────

    fun setVoice(voiceName: String, languageCode: String = "ur-IN") {
        SdkLogger.d(SUB, "setVoice voiceName=$voiceName languageCode=$languageCode")
        config = config?.copy(ttsVoiceName = voiceName, ttsLanguageCode = languageCode)
    }

    fun setSttModel(model: String) {
        SdkLogger.d(SUB, "setSttModel model=$model")
        config = config?.copy(sttModel = model)
    }

    // ── Dispose ───────────────────────────────────────────────────────────────

    fun dispose() {
        SdkLogger.d(SUB, "dispose")
        player.stop()
        repo?.disconnect()
        scope?.cancel()
        scope    = null
        repo     = null
        config   = null
        INSTANCE = null
        SdkLogger.i(SUB, "SDK disposed")
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun observeRepoEvents() {
        scope?.launch {
            repo?.events?.collect { event ->
                when (event) {
                    is InternalChatEvent.Connected -> {
                        SdkLogger.i(SUB, "Event: Connected")
                        emit(SdkEvent.Connected)
                    }

                    is InternalChatEvent.Disconnected -> {
                        SdkLogger.w(SUB, "Event: Disconnected reason=${event.reason}")
                        emit(SdkEvent.Disconnected(event.reason))
                    }

                    is InternalChatEvent.Error -> {
                        SdkLogger.e(SUB, "Event: Error msg=${event.message}")
                        emit(SdkEvent.Error(event.message))
                    }

                    is InternalChatEvent.MessageReceived -> {
                        SdkLogger.d(SUB, "Event: MessageReceived text=${event.text}")
                        emit(SdkEvent.BotMessageReceived(event.text))
                        speak(event.text)
                    }

                    is InternalChatEvent.ActionReceived -> {
                        val reqId = UUID.randomUUID().toString()
                        pendingRequestId = reqId
                        SdkLogger.d(SUB,
                            "Event: ActionReceived service=${event.action.serviceName} " +
                                    "actionId=${event.action.action_id} requestId=$reqId"
                        )
                        emit(SdkEvent.ActionRequired(
                            SdkAction(
                                serviceName = event.action.serviceName,
                                actionId    = event.action.action_id,
                                parameters  = event.action.parameters ?: emptyMap(),
                                requestId   = reqId
                            )
                        ))
                    }

                    is InternalChatEvent.BeneficiaryListRequested -> {
                        SdkLogger.d(SUB, "Event: BeneficiaryListRequested requestId=${event.requestId}")
                        emit(SdkEvent.BeneficiaryListRequested(event.requestId))
                    }
                }
            }
        }
    }

    private suspend fun runStt(base64Audio: String): String {
        val cfg = config ?: return "❌ SDK not initialised"
        SdkLogger.d(SUB, "runStt language=${cfg.language} model=${cfg.sttModel}")
        return withContext(Dispatchers.IO) {
            try {
                val request = SpeechV2Request(
                    recognizer = "projects/${cfg.gcpProjectId}/locations/global/recognizers/_",
                    config = SpeechV2Config(
                        explicitDecodingConfig = ExplicitDecodingConfig(
                            encoding          = "LINEAR16",
                            sampleRateHertz   = 16000,
                            audioChannelCount = 1
                        ),
                        languageCodes = if (cfg.language.equals("english", true))
                            listOf("en-US") else listOf("ur-PK"),
                        model = cfg.sttModel
                    ),
                    content = base64Audio
                )
                val response = sttClient!!.recognize(request)
                if (response.isSuccessful) {
                    val results = response.body()?.results
                    if (!results.isNullOrEmpty()) {
                        val text = results[0].alternatives?.get(0)?.transcript ?: "🔇 No transcript"
                        SdkLogger.d(SUB, "STT result: $text")
                        text
                    } else {
                        SdkLogger.w(SUB, "STT returned no results")
                        "🔇 No speech recognised"
                    }
                } else {
                    SdkLogger.e(SUB, "STT HTTP error ${response.code()}: ${response.errorBody()?.string()}")
                    "❌ STT error ${response.code()}"
                }
            } catch (e: SocketTimeoutException) {
                SdkLogger.e(SUB, "STT timeout", e)
                "❌ STT timeout – please retry"
            } catch (e: Exception) {
                SdkLogger.e(SUB, "STT exception: ${e.message}", e)
                "❌ ${e.message}"
            }
        }
    }

    private fun emit(event: SdkEvent) {
        _events.postValue(event)
    }
}
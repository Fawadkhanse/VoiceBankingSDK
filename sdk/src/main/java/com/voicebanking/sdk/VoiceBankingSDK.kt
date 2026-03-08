package com.voicebanking.sdk

import android.content.Context
import android.util.Base64
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.util.UUID

/**
 * # VoiceBankingSDK
 *
 * Single entry-point for voice-based banking interactions.
 *
 * ## Quick start
 * ```kotlin
 * val sdk = VoiceBankingSDK.getInstance()
 *
 * sdk.events.observe(viewLifecycleOwner) { event ->
 *     when (event) {
 *         is SdkEvent.Connected          -> { /* ready */ }
 *         is SdkEvent.BotMessageReceived -> showMessage(event.text)
 *         is SdkEvent.ActionRequired     -> showConfirmDialog(event.action)
 *         is SdkEvent.BeneficiaryListRequested -> sdk.sendBeneficiaryList(event.requestId, myList)
 *         else -> { }
 *     }
 * }
 *
 * sdk.init(
 *     context = requireContext(),
 *     config  = VoiceBankingConfig(
 *         chatApiUrl     = "http://192.168.1.1:5012",
 *         googleApiKey   = "AIza…",
 *         gcpProjectId   = "my-project",
 *         gcpClientEmail = "svc@my-project.iam.gserviceaccount.com",
 *         gcpPrivateKey  = "-----BEGIN PRIVATE KEY-----\n…"
 *     )
 * )
 *
 * // Start / stop recording
 * sdk.startRecording()
 * sdk.stopAndProcess()
 *
 * // Confirm or cancel a pending action
 * sdk.confirmAction(requestId, action)
 * sdk.cancelAction(requestId, action)
 *
 * // Send beneficiary list when requested
 * sdk.sendBeneficiaryList(requestId, listOf(SdkBeneficiary("Ali", "HBL")))
 *
 * // Play arbitrary text via TTS
 * sdk.speak("آپ کا بیلنس ایک لاکھ روپے ہے")
 *
 * // Release resources
 * sdk.dispose()
 * ```
 */
class VoiceBankingSDK private constructor() {

    // ─────────────────────────────────────────────────────────────────────────
    // Singleton
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "VoiceBankingSDK"

        @Volatile private var INSTANCE: VoiceBankingSDK? = null

        /** Returns the singleton SDK instance. */
        @JvmStatic
        fun getInstance(): VoiceBankingSDK =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceBankingSDK().also { INSTANCE = it }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public LiveData – host app observes this
    // ─────────────────────────────────────────────────────────────────────────

    private val _events = MutableLiveData<SdkEvent>()

    /**
     * Observe SDK events from your Fragment/Activity:
     * ```kotlin
     * sdk.events.observe(viewLifecycleOwner) { event -> … }
     * ```
     */
    val events: LiveData<SdkEvent> get() = _events

    // ─────────────────────────────────────────────────────────────────────────
    // Internal state
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initialise with all default credentials bundled in the SDK.
     * Simplest possible usage:
     * ```kotlin
     * VoiceBankingSDK.getInstance().init(requireContext())
     * ```
     */
    fun init(context: Context) = init(context, VoiceBankingConfig())

    /**
     * Initialise the SDK and connect to the backend.
     * Call once after obtaining RECORD_AUDIO permission.
     *
     * @param context Any context (ApplicationContext is fine)
     * @param config  [VoiceBankingConfig] – omit to use built-in defaults
     */
    fun init(context: Context, config: VoiceBankingConfig) {
        if (this.config != null) {
            Log.d(TAG, "SDK already initialised – skipping")
            return
        }
        this.config = config
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
                Log.e(TAG, "Init error: ${e.message}")
                emit(SdkEvent.Error("Connection failed: ${e.message}"))
            }
        }
    }

    /** @return true if [init] has already been called successfully. */
    fun isInitialized(): Boolean = config != null

    // ─────────────────────────────────────────────────────────────────────────
    // Recording
    // ─────────────────────────────────────────────────────────────────────────

    /** Start capturing audio from the microphone. */
    fun startRecording() {
        player.stop()
        didStartRecording = true
        emit(SdkEvent.RecordingStarted)
        recorder.startRecording()
    }

    /**
     * Stop recording, run STT, send transcript to the bank backend.
     * Results arrive via [events] as [SdkEvent.TranscriptReady] then [SdkEvent.BotMessageReceived].
     */
    fun stopAndProcess() {
        if (!didStartRecording) return
        didStartRecording = false
        emit(SdkEvent.RecordingStopped)

        scope?.launch {
            val base64Audio = withContext(Dispatchers.IO) { recorder.stopRecording() }
            if (base64Audio.isEmpty()) {
                emit(SdkEvent.Error("No audio captured"))
                return@launch
            }

            val transcript = runStt(base64Audio)
            if (transcript.startsWith("❌") || transcript.startsWith("🔇")) {
                emit(SdkEvent.Error(transcript))
                return@launch
            }

            emit(SdkEvent.TranscriptReady(transcript))
            repo?.sendUserMessage(transcript)
                ?: emit(SdkEvent.Error("SDK not initialised"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call after user confirms an action surfaced via [SdkEvent.ActionRequired].
     */
    fun confirmAction(requestId: String, action: SdkAction) {
        repo?.sendActionStatus(
            requestId   = requestId,
            actionId    = action.actionId,
            serviceName = action.serviceName,
            status      = "confirmed",
            message     = "User confirmed"
        )
    }

    /**
     * Call after user cancels an action surfaced via [SdkEvent.ActionRequired].
     */
    fun cancelAction(requestId: String, action: SdkAction) {
        repo?.sendActionStatus(
            requestId   = requestId,
            actionId    = action.actionId,
            serviceName = action.serviceName,
            status      = "canceled",
            message     = "User canceled"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Beneficiary list
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Supply the beneficiary list when the SDK emits [SdkEvent.BeneficiaryListRequested].
     */
    fun sendBeneficiaryList(requestId: String, beneficiaries: List<SdkBeneficiary>) {
        val mapped = beneficiaries.map {
            mapOf("beneficiary_name" to it.beneficiaryName, "bank_name" to it.bankName)
        }
        repo?.sendBeneficiaryList(requestId, mapped)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TTS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Synthesise [text] with Google TTS and play it.
     * Emits [SdkEvent.PlaybackStarted] and [SdkEvent.PlaybackFinished].
     */
    fun speak(text: String) {
        val cfg = config ?: run { emit(SdkEvent.Error("SDK not initialised")); return }
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
                    player.play(pcm, 24000)
                    emit(SdkEvent.PlaybackFinished)
                } else {
                    emit(SdkEvent.Error("TTS error ${response.code()}"))
                }
            } catch (e: Exception) {
                emit(SdkEvent.Error("TTS exception: ${e.message}"))
            }
        }
    }

    /** Stop any ongoing TTS playback. */
    fun stopPlayback() = player.stop()

    // ─────────────────────────────────────────────────────────────────────────
    // Language & voice helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Dynamically change TTS voice (e.g. switch male/female). */
    fun setVoice(voiceName: String, languageCode: String = "ur-IN") {
        config = config?.copy(ttsVoiceName = voiceName, ttsLanguageCode = languageCode)
    }

    /** Dynamically change STT model. */
    fun setSttModel(model: String) {
        config = config?.copy(sttModel = model)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dispose
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Release all resources. Call from `onDetach()` / `onDestroy()`.
     * The singleton can be re-initialised by calling [init] again.
     */
    fun dispose() {
        player.stop()
        repo?.disconnect()
        scope?.cancel()
        scope  = null
        repo   = null
        config = null
        INSTANCE = null
        Log.d(TAG, "SDK disposed")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeRepoEvents() {
        scope?.launch {
            repo?.events?.collect { event ->
                when (event) {
                    is InternalChatEvent.Connected ->
                        emit(SdkEvent.Connected)

                    is InternalChatEvent.Disconnected ->
                        emit(SdkEvent.Disconnected(event.reason))

                    is InternalChatEvent.Error ->
                        emit(SdkEvent.Error(event.message))

                    is InternalChatEvent.MessageReceived -> {
                        emit(SdkEvent.BotMessageReceived(event.text))
                        // Auto-speak the bot reply
                        speak(event.text)
                    }

                    is InternalChatEvent.ActionReceived -> {
                        val reqId = UUID.randomUUID().toString()
                        pendingRequestId = reqId
                        emit(SdkEvent.ActionRequired(
                            SdkAction(
                                serviceName = event.action.serviceName,
                                actionId    = event.action.action_id,
                                parameters  = event.action.parameters ?: emptyMap(),
                                requestId   = reqId
                            )
                        ))
                    }

                    is InternalChatEvent.BeneficiaryListRequested ->
                        emit(SdkEvent.BeneficiaryListRequested(event.requestId))
                }
            }
        }
    }

    private suspend fun runStt(base64Audio: String): String {
        val cfg = config ?: return "❌ SDK not initialised"
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
                    if (!results.isNullOrEmpty())
                        results[0].alternatives?.get(0)?.transcript ?: "🔇 No transcript"
                    else
                        "🔇 No speech recognised"
                } else {
                    "❌ STT error ${response.code()}"
                }
            } catch (e: SocketTimeoutException) {
                "❌ STT timeout – please retry"
            } catch (e: Exception) {
                "❌ ${e.message}"
            }
        }
    }

    private fun emit(event: SdkEvent) {
        _events.postValue(event)
    }
}
package com.voicebanking.demo

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.voicebanking.demo.databinding.ActivityDemoBinding
import com.voicebanking.sdk.VoiceBankingSDK
import com.voicebanking.sdk.models.SdkAction
import com.voicebanking.sdk.models.SdkBeneficiary
import com.voicebanking.sdk.models.SdkEvent
import com.voicebanking.sdk.models.VoiceBankingConfig

class DemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDemoBinding
    private val sdk = VoiceBankingSDK.getInstance()

    // Sample beneficiary list – replace with your real data source
    private val myBeneficiaries = listOf(
        SdkBeneficiary("Muhammad Ali",   "BankIslami"),
        SdkBeneficiary("Nabeel Hussain", "UBL"),
        SdkBeneficiary("Sara Khan",      "HBL")
    )

    private var lastBotMessage = ""

    // ─────────────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkMicPermission()
        initSdk()
        observeSdk()

        // Hold-to-record
        binding.btnRecord.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (hasMicPermission()) sdk.startRecording()
                    else checkMicPermission()
                    true
                }
                MotionEvent.ACTION_UP -> { sdk.stopAndProcess(); true }
                else -> false
            }
        }

        binding.btnSpeak.setOnClickListener {
            if (lastBotMessage.isNotEmpty()) sdk.speak(lastBotMessage)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SDK initialisation
    // ─────────────────────────────────────────────────────────────────────────

    private fun initSdk() {
        if (sdk.isInitialized()) return
        // All credentials are bundled in the SDK — no config needed
        sdk.init(context = applicationContext, config = VoiceBankingConfig(enableLogging = true))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Observe SDK events
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeSdk() {
        sdk.events.observe(this) { event ->
            when (event) {

                is SdkEvent.Connected ->
                    setStatus("✅ Connected — hold button to speak")

                is SdkEvent.Disconnected ->
                    setStatus("⚠️ Disconnected: ${event.reason}")

                is SdkEvent.Error ->
                    setStatus("❌ ${event.message}")

                is SdkEvent.RecordingStarted -> {
                    setStatus("🔴 Recording…")
                    tintRecord(recording = true)
                }

                is SdkEvent.RecordingStopped -> {
                    setStatus("⏳ Processing…")
                    tintRecord(recording = false)
                }

                is SdkEvent.TranscriptReady ->
                    appendConversation("You", event.text)

                is SdkEvent.BotMessageReceived -> {
                    lastBotMessage = event.text
                    appendConversation("Bot", event.text)
                    binding.btnSpeak.isEnabled = true
                }

                is SdkEvent.PlaybackStarted ->
                    setStatus("🔊 Playing response…")

                is SdkEvent.PlaybackFinished ->
                    setStatus("✅ Done — hold button to speak again")

                is SdkEvent.ActionRequired ->
                    showActionConfirmation(event.action)

                is SdkEvent.BeneficiaryListRequested ->{
                    Log.d("TAG", "BeneficiaryListRequested$myBeneficiaries")
                    sdk.sendBeneficiaryList(event.requestId, myBeneficiaries)}
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Action confirmation dialog
    // ─────────────────────────────────────────────────────────────────────────

    private fun showActionConfirmation(action: SdkAction) {
        val params = action.parameters.entries.joinToString("\n") { (k, v) -> "• $k: $v" }
        AlertDialog.Builder(this)
            .setTitle("⚠️ ${action.serviceName.uppercase()}")
            .setMessage("Do you want to confirm this action?\n\n$params")
            .setPositiveButton("✅ Confirm") { _, _ ->
                sdk.confirmAction(action.requestId, action)
            }
            .setNegativeButton("❌ Cancel") { _, _ ->
                sdk.cancelAction(action.requestId, action)
            }
            .setCancelable(false)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun setStatus(msg: String) {
        binding.tvStatus.text = msg
    }

    private fun appendConversation(speaker: String, text: String) {
        val current = binding.tvTranscript.text.toString()
            .let { if (it == "Press and hold the button to speak…") "" else "$it\n\n" }
        binding.tvTranscript.text = "$current$speaker: $text"
    }

    private fun tintRecord(recording: Boolean) {
        binding.btnRecord.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (recording) 0xFF880000.toInt() else 0xFFE63946.toInt()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permissions
    // ─────────────────────────────────────────────────────────────────────────

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun checkMicPermission() {
        if (!hasMicPermission())
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), 101)
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onDestroy() {
        sdk.dispose()
        super.onDestroy()
    }
}
# VoiceBankingSDK

An Android SDK (AAR library) for voice-powered banking interactions.  
Bundles Google Cloud STT v2, Google Cloud TTS v1, and a WebSocket-based bank chat backend into a single easy-to-use API.

---

## Features

- 🎙 **Speech-to-Text** (Google Cloud STT v2 – supports Urdu & English)
- 🔊 **Text-to-Speech** (Google Cloud TTS v1 – Urdu Standard voices)
- 🔌 **WebSocket chat** session with bank backend
- ✅ **Action confirmation** flow (funds transfer, bill payment, etc.)
- 👥 **Beneficiary list** supply callback
- 📦 Single-class API surface – `VoiceBankingSDK.getInstance()`

---

## Project Structure

```
VoiceBankingSDK/
├── sdk/                        ← Library module (produces the AAR)
│   └── src/main/java/com/voicebanking/sdk/
│       ├── VoiceBankingSDK.kt  ← Public entry point
│       ├── models/Models.kt    ← All public + internal models
│       ├── data/
│       │   ├── BankChatRepository.kt
│       │   ├── ServiceAccountAuth.kt
│       │   ├── SpeechV2Client.kt
│       │   └── TtsClient.kt
│       └── helper/
│           ├── AudioRecorder.kt
│           └── PcmPlayer.kt
├── app/                        ← Demo application
│   └── src/main/java/com/voicebanking/demo/
│       └── DemoActivity.kt     ← Full integration example
└── README.md
```

---

## Building the AAR

```bash
# From the project root:
./gradlew :sdk:assembleRelease

# Output:
# sdk/build/outputs/aar/sdk-release.aar
```

### Publish to local Maven (optional)

```bash
./gradlew :sdk:publishToMavenLocal
# Installs to ~/.m2/repository/com/voicebanking/sdk/1.0.0/
```

---

## Integration

### Option A – Copy AAR directly

1. Copy `sdk-release.aar` to `app/libs/`
2. In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/sdk-release.aar"))

    // Required transitive dependencies
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
```

### Option B – Local Maven

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal()   // ← add this
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.voicebanking:sdk:1.0.0")
}
```

### Manifest permissions

The AAR merges these automatically; confirm they appear in your merged manifest:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.INTERNET"/>
```

### Network security (HTTP server)

If your bank API uses plain HTTP, add to `res/xml/network_security_config.xml`:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="true"/>
</network-security-config>
```

---

## Usage

### 1. Initialise (once per session)

```kotlin
val sdk = VoiceBankingSDK.getInstance()

sdk.init(
    context = requireContext(),
    config  = VoiceBankingConfig(
        chatApiUrl     = "http://192.168.1.1:5012",
        googleApiKey   = "AIza…",
        gcpProjectId   = "my-gcp-project",
        gcpClientEmail = "svc@my-project.iam.gserviceaccount.com",
        gcpPrivateKey  = """-----BEGIN PRIVATE KEY-----
MIIEv…
-----END PRIVATE KEY-----""",
        language        = "urdu",              // "english" or "urdu"
        sttModel        = "long",              // "long" | "latest_long"
        ttsVoiceName    = "ur-IN-Standard-B",  // Standard-A (female) | Standard-B (male)
        ttsLanguageCode = "ur-IN",
        enableLogging   = false
    )
)
```

### 2. Observe events

```kotlin
sdk.events.observe(viewLifecycleOwner) { event ->
    when (event) {
        is SdkEvent.Connected              -> showReady()
        is SdkEvent.Disconnected           -> showDisconnected(event.reason)
        is SdkEvent.Error                  -> showError(event.message)
        is SdkEvent.RecordingStarted       -> showRecordingUI()
        is SdkEvent.RecordingStopped       -> showProcessingUI()
        is SdkEvent.TranscriptReady        -> showTranscript(event.text)
        is SdkEvent.BotMessageReceived     -> showBotReply(event.text)
        is SdkEvent.PlaybackStarted        -> showPlayingUI()
        is SdkEvent.PlaybackFinished       -> showIdleUI()
        is SdkEvent.ActionRequired         -> showConfirmDialog(event.action)
        is SdkEvent.BeneficiaryListRequested -> sdk.sendBeneficiaryList(
            event.requestId,
            myBeneficiaries    // List<SdkBeneficiary>
        )
    }
}
```

### 3. Record & process

```kotlin
// Hold-to-record pattern
button.setOnTouchListener { _, event ->
    when (event.action) {
        MotionEvent.ACTION_DOWN -> sdk.startRecording()
        MotionEvent.ACTION_UP   -> sdk.stopAndProcess()
    }
    true
}
```

### 4. Confirm / cancel actions

```kotlin
private fun showConfirmDialog(action: SdkAction) {
    AlertDialog.Builder(this)
        .setTitle(action.serviceName)
        .setMessage(action.parameters.toString())
        .setPositiveButton("Confirm") { _, _ -> sdk.confirmAction(action.requestId, action) }
        .setNegativeButton("Cancel")  { _, _ -> sdk.cancelAction(action.requestId, action)  }
        .show()
}
```

### 5. Supply beneficiary list

```kotlin
val myBeneficiaries = listOf(
    SdkBeneficiary("Muhammad Ali",   "BankIslami"),
    SdkBeneficiary("Nabeel Hussain", "UBL")
)
// called automatically when SdkEvent.BeneficiaryListRequested fires
sdk.sendBeneficiaryList(requestId, myBeneficiaries)
```

### 6. Play arbitrary text

```kotlin
sdk.speak("Your balance is fifty thousand rupees")
```

### 7. Switch voice / model at runtime

```kotlin
sdk.setVoice("ur-IN-Standard-A")       // switch to female voice
sdk.setSttModel("latest_long")         // switch STT model
```

### 8. Dispose

```kotlin
override fun onDestroy() {
    sdk.dispose()
    super.onDestroy()
}
```

---

## SdkEvent reference

| Event | Description |
|---|---|
| `Connected` | WebSocket session ready |
| `Disconnected(reason)` | WebSocket closed |
| `Error(message)` | Non-fatal error |
| `RecordingStarted` | Mic active |
| `RecordingStopped` | Mic stopped, STT running |
| `TranscriptReady(text)` | STT result |
| `BotMessageReceived(text)` | Server reply text |
| `PlaybackStarted` | TTS audio playing |
| `PlaybackFinished` | TTS audio done |
| `ActionRequired(action)` | Server wants action confirmed |
| `BeneficiaryListRequested(requestId)` | Server needs beneficiary list |

---

## VoiceBankingConfig reference

| Field | Required | Default | Description |
|---|---|---|---|
| `chatApiUrl` | ✅ | – | Bank API base URL |
| `googleApiKey` | ✅ | – | Google Cloud API key (TTS) |
| `gcpProjectId` | ✅ | – | GCP project ID (STT) |
| `gcpClientEmail` | ✅ | – | Service account email |
| `gcpPrivateKey` | ✅ | – | PEM private key |
| `language` | – | `"urdu"` | `"urdu"` or `"english"` |
| `sttModel` | – | `"long"` | `"long"` or `"latest_long"` |
| `ttsVoiceName` | – | `"ur-IN-Standard-B"` | Google TTS voice |
| `ttsLanguageCode` | – | `"ur-IN"` | Google TTS language |
| `enableLogging` | – | `false` | Verbose HTTP/WS logs |

---

## Requirements

- Android minSdk 24+
- `RECORD_AUDIO` permission granted before calling `startRecording()`
- Google Cloud project with Speech-to-Text v2 and Text-to-Speech v1 APIs enabled
- Service account with `roles/speech.client` and `roles/texttospeech.serviceAgent`

package com.voicebanking.sdk.utils



import android.util.Log

/**
 * Centralised logger for the VoiceBankingSDK.
 *
 * All SDK log lines share the same tag so you can filter in Logcat with:
 *   Tag = "VoiceSDK"
 *
 * Logging is disabled by default and enabled via [VoiceBankingConfig.enableLogging]
 * or [VoiceBankingSDK.init].
 */
internal object SdkLogger {

    /** Single tag used across every SDK class — filter by this in Logcat. */
    const val TAG = "VoiceSDK"

    @Volatile var enabled: Boolean = false

    fun d(msg: String)               { if (enabled) Log.d(TAG, msg) }
    fun d(subtag: String, msg: String) { if (enabled) Log.d(TAG, "[$subtag] $msg") }
    fun e(msg: String)               { Log.e(TAG, msg) }          // errors always logged
    fun e(subtag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.e(TAG, "[$subtag] $msg", t)
        else           Log.e(TAG, "[$subtag] $msg")
    }
    fun w(subtag: String, msg: String) { if (enabled) Log.w(TAG, "[$subtag] $msg") }
    fun i(subtag: String, msg: String) { if (enabled) Log.i(TAG, "[$subtag] $msg") }
}
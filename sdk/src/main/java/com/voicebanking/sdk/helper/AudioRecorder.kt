package com.voicebanking.sdk.helper

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log

internal class AudioRecorder {

    private val sampleRate    = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat   = AudioFormat.ENCODING_PCM_16BIT

    private val bufferSize = maxOf(
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 4,
        4096
    )

    private var audioRecord:     AudioRecord? = null
    private var recordingThread: Thread?      = null

    @Volatile private var isRecording = false
    private val audioData = mutableListOf<Byte>()

    fun startRecording() {
        cleanup()
        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormat, bufferSize
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("SDK_Audio", "Failed to initialise AudioRecord")
                recorder.release()
                return
            }
            audioRecord = recorder
            audioData.clear()
            isRecording = true
            recorder.startRecording()

            recordingThread = Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val read = recorder.read(buffer, 0, bufferSize)
                    when {
                        read > 0 -> synchronized(audioData) { audioData.addAll(buffer.take(read)) }
                        read == AudioRecord.ERROR_INVALID_OPERATION -> break
                        read == AudioRecord.ERROR_BAD_VALUE         -> break
                    }
                }
            }.also { it.start() }
        } catch (e: SecurityException) {
            Log.e("SDK_Audio", "Permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e("SDK_Audio", "Error: ${e.message}")
        }
    }

    fun stopRecording(): String {
        try { audioRecord?.stop() } catch (e: Exception) { /* ignore */ }
        isRecording = false
        try { recordingThread?.join(2000) } catch (_: InterruptedException) {}
        recordingThread = null
        try { audioRecord?.release() } catch (e: Exception) { /* ignore */ }
        audioRecord = null

        val bytes = synchronized(audioData) { audioData.toByteArray() }
        Log.d("SDK_Audio", "Captured ${bytes.size} bytes")
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun cleanup() {
        isRecording = false
        try { audioRecord?.stop() }  catch (_: Exception) {}
        try { recordingThread?.join(1000) } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        recordingThread = null
        audioData.clear()
    }
}

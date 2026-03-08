package com.voicebanking.sdk.helper

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PcmPlayer {

    private var audioTrack: AudioTrack? = null

    suspend fun play(pcm: ByteArray, sampleRate: Int = 24000) = withContext(Dispatchers.IO) {
        val ch  = AudioFormat.CHANNEL_OUT_MONO
        val fmt = AudioFormat.ENCODING_PCM_16BIT
        val buf = AudioTrack.getMinBufferSize(sampleRate, ch, fmt).coerceAtLeast(pcm.size)

        stop()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(fmt)
                    .setSampleRate(sampleRate)
                    .setChannelMask(ch)
                    .build()
            )
            .setBufferSizeInBytes(buf)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.write(pcm, 0, pcm.size)
        audioTrack?.play()

        // Wait for playback to finish
        Thread.sleep((pcm.size.toLong() * 1000L) / (sampleRate * 2) + 300)
    }

    fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}

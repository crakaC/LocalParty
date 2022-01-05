package com.crakac.localparty.encode

import android.media.AudioFormat
import android.media.MediaFormat

class AudioEncoderConfig private constructor() {
    companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val NANOS_PER_MICROS = 1_000L
        const val MIME_TYPE_AAC = MediaFormat.MIMETYPE_AUDIO_AAC
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        const val CHANNEL_COUNT = 2
        const val BYTES_PER_FRAME = 2 * CHANNEL_COUNT
        const val SAMPLE_RATE = 44_100
        const val BIT_RATE = CHANNEL_COUNT * 64 * 1024 // 64kbps per channel
    }
}
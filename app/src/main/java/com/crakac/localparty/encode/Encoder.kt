package com.crakac.localparty.encode

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

interface Encoder : Codec {
    enum class Type { Video, Audio }

    val type: Type

    interface Callback {
        fun onFormatChanged(format: MediaFormat, type: Type)
        fun onEncoded(buffer: ByteBuffer, info: MediaCodec.BufferInfo, type: Type)
    }
}
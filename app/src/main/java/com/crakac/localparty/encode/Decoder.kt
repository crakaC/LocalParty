package com.crakac.localparty.encode

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

interface Decoder : Codec {
    enum class Type { Video, Audio }

    val decoderType: Type

    interface Callback {
        fun onFormatChanged(format: MediaFormat, type: Type)
        fun onDecoded(buffer: ByteBuffer, info: MediaCodec.BufferInfo, type: Type)
    }
}
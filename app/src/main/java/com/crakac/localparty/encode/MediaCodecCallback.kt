package com.crakac.localparty.encode

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log

abstract class MediaCodecCallback(
    private val tag: String = MediaCodecCallback::class.java.simpleName
) : MediaCodec.Callback() {
    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}

    override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        Log.e(tag, e.stackTraceToString())
    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        runCatching {
            Log.d(tag, "${codec.name} format changed: $format")
        }
    }
}
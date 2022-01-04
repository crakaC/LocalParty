package com.crakac.localparty.encode

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.nio.ByteBuffer

class MediaEncoder(
    width: Int,
    height: Int,
    private val callback: MediaEncoderCallback,
) : Encoder.Callback {
    companion object {
        private val TAG = MediaEncoder::class.java.simpleName
    }

    interface MediaEncoderCallback {
        fun onEncoded(data: ByteArray, presentationTimeUs: Long, type: Encoder.Type)
        fun onFormatChanged(csd: ByteArray, type: Encoder.Type)
    }

    /**
     * Input surface of video encoder.
     *
     * Before using this surface, you must call [prepare()].
     */
    val inputSurface: Surface
        get() = videoEncoder.inputSurface

    private val videoEncoder = VideoEncoder(width, height, this)
    private val audioEncoder = AudioEncoder(this)

    override fun onFormatChanged(encoder: Encoder, format: MediaFormat) {
        val csd = format.getByteBuffer("csd-0")?.array() ?: return
        callback.onFormatChanged(csd, encoder.type)
    }

    override fun onEncoded(encoder: Encoder, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val byteArray = ByteArray(info.size)
        buffer.position(info.offset).limit(info.offset + info.size)
        buffer.get(byteArray)
        callback.onEncoded(byteArray, info.presentationTimeUs, encoder.type)
    }

    fun prepare() {
        videoEncoder.prepare()
        audioEncoder.prepare()
    }

    fun start() {
        videoEncoder.start()
        audioEncoder.start()
    }

    fun stop() {
        videoEncoder.stop()
        audioEncoder.stop()
    }

    fun release() {
        videoEncoder.release()
        audioEncoder.release()
    }

}
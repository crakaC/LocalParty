package com.crakac.localparty.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
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
        fun onCodecSpecificData(csd: ByteArray, type: Encoder.Type)
    }

    /**
     * Input surface of video encoder.
     *
     * Before using this surface, you must call [prepare()].
     */
    val inputSurface: Surface
        get() = videoEncoder.inputSurface

    private val videoEncoder = VideoEncoder(width, height, this)
    private val audioEncoder = AsyncAudioEncoder(this)

    override fun onFormatChanged(format: MediaFormat, type: Encoder.Type) {
    }

    override fun onCSD(csd: ByteArray, type: Encoder.Type) {
        Log.d(TAG, "onCSD csd=[${csd.joinToString()}]")
        callback.onCodecSpecificData(csd, type)
    }

    override fun onEncoded(buffer: ByteBuffer, info: MediaCodec.BufferInfo, type: Encoder.Type) {
        val byteArray = ByteArray(info.size)
        buffer.position(info.offset).limit(info.offset + info.size)
        buffer.get(byteArray)
        callback.onEncoded(byteArray, info.presentationTimeUs, type)
    }

    fun prepare() {
        videoEncoder.configure()
        audioEncoder.configure()
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
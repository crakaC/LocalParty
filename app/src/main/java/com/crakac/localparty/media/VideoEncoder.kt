package com.crakac.localparty.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import java.nio.ByteBuffer

class VideoEncoder(width: Int, height: Int, private val callback: Encoder.Callback) :
    AsyncCodec(MIME, isEncoder = true) {
    companion object {
        private const val FRAME_RATE = 30
        private const val BIT_RATE = 3 * 1024 * 1024 // 3Mbps
        private const val I_FRAME_INTERVAL_SEC = 2.0f
        private const val REPEAT_FRAMES_AFTER_MICRO_SEC = 1_000_000L / 30
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val TAG = "VideoEncoder"
    }
    val type = Encoder.Type.Video

    lateinit var inputSurface: Surface
        private set

    override val format: MediaFormat =
        MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SEC)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_FRAMES_AFTER_MICRO_SEC)
        }

    override fun onCodecOutputBufferAvailable(
        codec: MediaCodec,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        index: Int
    ) {
        callback.onEncoded(buffer, info, type)
        codec.releaseOutputBuffer(index, false)
    }

    override fun onCodecOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        callback.onFormatChanged(format, type)
    }

    override fun onCodecSpecificData(csd: ByteArray) {
        callback.onCSD(csd, type)
    }

    override fun onConfigured(codec: MediaCodec) {
        inputSurface = codec.createInputSurface()
    }

    override fun onRequestEOS(codec: MediaCodec) {
        codec.signalEndOfInputStream()
    }
}
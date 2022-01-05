package com.crakac.localparty.encode

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface

class VideoEncoder(width: Int, height: Int, private val callback: Encoder.Callback) :
    Encoder, MediaCodecCallback() {
    companion object {
        private const val FRAME_RATE = 30
        private const val BIT_RATE = 3 * 1024 * 1024 // 3Mbps
        private const val I_FRAME_INTERVAL_SEC = 2.0f
        private const val REPEAT_FRAMES_AFTER_MICRO_SEC = 1_000_000L / 30
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val TAG = "VideoEncoder"
    }

    override val type = Encoder.Type.Video
    lateinit var inputSurface: Surface
        private set

    private val format: MediaFormat =
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

    private val codec = MediaCodec.createEncoderByType(MIME).apply {
        setCallback(this@VideoEncoder)
    }

    override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
        val buffer = codec.getOutputBuffer(index) ?: throw RuntimeException("Output buffer is null")
        callback.onEncoded(buffer, info, type)
        codec.releaseOutputBuffer(index, false)
    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        callback.onFormatChanged(format, type)
    }

    override fun configure() {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
    }

    override fun start() {
        codec.start()
    }

    override fun stop() {
        codec.stop()
    }

    override fun release() {
        codec.release()
    }
}
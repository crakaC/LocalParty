package com.crakac.localparty.encode

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Wrapper class of MediaCodec
 */
abstract class AsyncCodec(mime: String, val isEncoder: Boolean) :
    MediaCodecCallback() {
    companion object {
        private val TAG = AsyncCodec::class.java.simpleName
        private const val RELEASE_TIMEOUT_MILLIS = 3000L
    }

    enum class State { Initial, Running, RequestEOS, WaitEOS, Stopped }

    open fun onStart() {}
    open fun onConfigured() {}
    open fun onRequestEOS() {}
    open fun onEOS() {}
    open fun onCodecOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
    open fun onCodecSpecificData(csd: ByteArray) {}

    /**
     * Called when input buffer is available. After [stop()] is called, this method will never be invoked.
     */
    abstract fun onCodecInputBufferAvailable(codec: MediaCodec, index: Int)

    /**
     * Called when output buffer is available.
     * @param buffer This buffer is not automatically released. You must call codec.releaseOutputBuffer() with index after you are done with buffer.
     * @param info (info.flag and (MediaCodec.BUFFER_FLAG_END_OF_STREAM or MediaCodec.BUFFER_FLAG_CODEC_CONFIG)) is always 0.
     * If you want to handle these, use [onEOS()] or [onCodecOutputFormatChanged()]
     */
    abstract fun onCodecOutputBufferAvailable(
        codec: MediaCodec,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        index: Int
    )

    private val handlerThread = HandlerThread("$TAG:$mime").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private var state: State = State.Initial
    private val codec = MediaCodec.createEncoderByType(mime).also {
        it.setCallback(this, handler)
    }
    abstract val format: MediaFormat

    final override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
        if (state == State.RequestEOS) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            state = State.WaitEOS
            return
        }
        onCodecInputBufferAvailable(codec, index)
    }

    final override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            Log.d(TAG, "END_OF_STREAM")
            codec.releaseOutputBuffer(index, false)
            state = State.Stopped
            onEOS()
            return
        }
        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            Log.d(TAG, "CODEC_CONFIG")
            onCodecOutputFormatChanged(codec, codec.outputFormat)
            val buffer = codec.getOutputBuffer(index) ?: return
            buffer.position(info.offset).limit(info.offset + info.size)
            val csd = ByteArray(info.size)
            buffer.get(csd)
            onCodecSpecificData(csd)
            codec.releaseOutputBuffer(index, false)
        } else {
            val buffer = codec.getOutputBuffer(index) ?: return
            onCodecOutputBufferAvailable(codec, buffer, info, index)
        }
    }

    fun configure() {
        val flag = if (isEncoder) MediaCodec.CONFIGURE_FLAG_ENCODE else 0
        codec.configure(format, null, null, flag)
        onConfigured()
    }

    fun start() {
        state = State.Running
        codec.start()
        onStart()
    }

    fun stop() {
        if (state != State.Running) {
            Log.d(TAG, "stop() is called but codec is not running. (state : $state)")
            return
        }
        state = State.RequestEOS
        onRequestEOS()
    }

    fun release() {
        thread {
            val timeoutMillis = RELEASE_TIMEOUT_MILLIS + System.currentTimeMillis()
            while (state != State.Stopped && timeoutMillis > System.currentTimeMillis()) {
                Thread.sleep(10)
            }
            codec.release()
            Log.d(TAG, "released")
        }
    }
}
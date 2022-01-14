package com.crakac.localparty.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Wrapper class of MediaCodec
 */
abstract class AsyncCodec(
    mime: String,
    val isEncoder: Boolean,
    private val outputSurface: Surface? = null
) :
    MediaCodec.Callback() {
    companion object {
        private val TAG = AsyncCodec::class.java.simpleName
        private const val RELEASE_TIMEOUT_MILLIS = 1000L
    }

    private val codecTag = if (isEncoder) "$mime encoder" else "$mime decoder"

    enum class State { Initial, Running, RequestEOS, Stopped, Released }

    open fun onStart() {}
    open fun onConfigured(codec: MediaCodec) {}
    open fun onRequestEOS(codec: MediaCodec) {}
    open fun onEOS() {}
    open fun onCodecOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
    open fun onCodecSpecificData(csd: ByteArray) {}

    /**
     * Called when input buffer is available. After [stop()] is called, this method will never be invoked.
     */
    open fun onCodecInputBufferAvailable(codec: MediaCodec, inputBuffer: ByteBuffer, index: Int) {}

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

    var state: State = State.Initial
        private set

    val isRunning: Boolean
        get() = state == State.Running

    protected val codec: MediaCodec =
        (if (isEncoder)
            MediaCodec.createEncoderByType(mime)
        else
            MediaCodec.createDecoderByType(mime)).also { it.setCallback(this, handler) }

    abstract val format: MediaFormat

    final override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
        try {
            if (state == State.RequestEOS) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                Log.d(TAG, "queued EOS: $codecTag")
                state = State.Stopped
                return
            }
            if (!isRunning) return
            val buffer = codec.getInputBuffer(index) ?: return
            onCodecInputBufferAvailable(codec, buffer, index)
        } catch (e: IllegalStateException) {
            Log.w(TAG, e.stackTraceToString())
        }
    }

    final override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
        runCatching {
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                Log.d(TAG, "END_OF_STREAM $codecTag")
                codec.releaseOutputBuffer(index, false)
                state = State.Stopped
                onEOS()
                return
            }

            if (!isRunning) return

            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                // Obtain Codec-specific Data
                Log.d(TAG, "CODEC_CONFIG $codecTag")
                val buffer = codec.getOutputBuffer(index) ?: return
                buffer.position(info.offset).limit(info.offset + info.size)
                val csd = ByteArray(info.size)
                buffer.get(csd)
                Log.d(TAG, "csd = [${csd.joinToString { it.toInt().and(0xFF).toString(16) }}]")
                onCodecSpecificData(csd)
                codec.releaseOutputBuffer(index, false)
            } else {
                val buffer = codec.getOutputBuffer(index) ?: return
                onCodecOutputBufferAvailable(codec, buffer, info, index)
            }
        }
    }

    final override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        runCatching {
            // Getting codec.name may throw IllegalStateException
            Log.e(TAG, "onError at ${codec.name}")
        }
        Log.e(TAG, e.stackTraceToString())
    }

    final override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        onCodecOutputFormatChanged(codec, format)
    }

    fun configure() {
        val flag = if (isEncoder) MediaCodec.CONFIGURE_FLAG_ENCODE else 0
        codec.configure(format, outputSurface, null, flag)
        onConfigured(codec)
    }

    fun start() {
        state = State.Running
        codec.start()
        onStart()
    }

    fun stop() {
        Log.d(TAG, "stop() $codecTag")
        if (state != State.Running) {
            Log.d(TAG, "stop() is called but codec is not running. (state : $state)")
            return
        }
        state = State.RequestEOS
        onRequestEOS(codec)
    }

    fun release() {
        thread {
            val timeoutMillis = RELEASE_TIMEOUT_MILLIS + System.currentTimeMillis()
            while (state != State.Stopped && timeoutMillis > System.currentTimeMillis()) {
                Thread.sleep(100)
            }
            Log.d(TAG, "release: $codecTag")
            try {
                // Some device throws IllegalStateException here
                codec.stop()
            } catch (e: IllegalStateException) {
                Log.d(TAG, "$codecTag codec.stop() failed", e)
            }
            codec.release()
            if (state != State.Stopped) {
                Log.w(TAG, "timeout: $codecTag")
            }
            state = State.Released
            handlerThread.quitSafely()
        }
    }
}
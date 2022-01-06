package com.crakac.localparty.encode

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque

class AudioDecoder(private val callback: Callback) : AsyncCodec(MediaFormat.MIMETYPE_AUDIO_AAC, isEncoder = false) {
    interface Callback {
        fun onDecoded(
            buffer: ByteBuffer,
            index: Int,
            presentationTimeUs: Long
        )
    }

    companion object {
        private val TAG = AudioDecoder::class.java.simpleName
    }

    private val queue = LinkedBlockingDeque<Sample>()

    override val format = MediaFormat.createAudioFormat(
        MediaFormat.MIMETYPE_AUDIO_AAC,
        44_100,
        2
    )

    override fun onCodecInputBufferAvailable(
        codec: MediaCodec,
        inputBuffer: ByteBuffer,
        index: Int
    ) {
        try {
            while (isRunning && queue.isEmpty()) {
                Thread.sleep(10)
            }
            val (data, presentationTimeUs, flag) = queue.poll() ?: return
            inputBuffer.put(data, 0, data.size)
            codec.queueInputBuffer(index, 0, data.size, presentationTimeUs, flag)
        } catch (e: Exception) {
            Log.w(TAG, e.stackTraceToString())
        }
    }

    override fun onCodecOutputBufferAvailable(
        codec: MediaCodec,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        index: Int
    ) {
        // audioBuffer will be released at MediaSync.Callback#onAudioBufferConsumed()
        // Need not to call codec.releaseOutputBuffer() here.
        try {
            callback.onDecoded(buffer, index, info.presentationTimeUs)
        } catch (e: Exception) {
            Log.w(TAG, e.stackTraceToString())
        }
    }

    override fun onRequestEOS(codec: MediaCodec) {
        queue.clear()
    }

    fun enqueue(data: Sample) {
        if (!isRunning) {
            Log.d(TAG, "enqueue() is called when codec is not running. $state")
        }
        queue.offer(data)
    }

    fun releaseOutputBuffer(bufferId: Int) {
        if (isRunning) {
            codec.releaseOutputBuffer(bufferId, false)
        }
    }
}
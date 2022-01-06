package com.crakac.localparty.encode

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque

class VideoDecoder(width: Int, height: Int, outputSurface: Surface) :
    AsyncCodec(MediaFormat.MIMETYPE_VIDEO_AVC, isEncoder = false, outputSurface) {

    companion object {
        private val TAG = VideoDecoder::class.java.simpleName
    }

    private val videoQueue = LinkedBlockingDeque<Sample>()

    override fun onCodecInputBufferAvailable(
        codec: MediaCodec,
        inputBuffer: ByteBuffer,
        index: Int
    ) {
        try {
            while (isRunning && videoQueue.isEmpty()) {
                Thread.sleep(10)
            }
            val (data, presentationTimeUs, flag) = videoQueue.poll() ?: return
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
        try {
            // surface timestamp must contain media presentation time in nanoseconds.
            codec.releaseOutputBuffer(index, info.presentationTimeUs * 1000)
        } catch (e: Exception) {
            Log.w(TAG, e.stackTraceToString())
        }
    }

    override fun onRequestEOS(codec: MediaCodec) {
        videoQueue.clear()
    }

    fun enqueue(data: Sample) {
        if (!isRunning) {
            Log.d(TAG, "enqueue() is called when codec is not running. $state")
        }
        videoQueue.offer(data)
    }

    fun setScalingMode(scalingMode: Int) {
        codec.setVideoScalingMode(scalingMode)
    }

    override val format: MediaFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)

}
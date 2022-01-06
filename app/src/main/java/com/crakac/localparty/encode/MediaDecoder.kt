package com.crakac.localparty.encode

import android.media.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MediaDecoder(
    outputSurface: Surface,
    width: Int,
    height: Int
) {
    companion object {
        private val TAG = MediaDecoder::class.java.simpleName
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_COUNT = 2
    }

    private val mediaSyncCallback: MediaSync.Callback = object : MediaSync.Callback() {
        override fun onAudioBufferConsumed(
            sync: MediaSync,
            audioBuffer: ByteBuffer,
            bufferId: Int
        ) {
            runCatching {
                audioDecoder.releaseOutputBuffer(bufferId)
            }
        }
    }
    private val handlerThread = HandlerThread("MediaSyncHandler").apply { start() }
    private val mediaSync = MediaSync().apply {
        setSurface(outputSurface)
        setCallback(mediaSyncCallback, Handler(handlerThread.looper))
    }

    private val syncSurface = mediaSync.createInputSurface()

    private val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .build()
        )
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .setBufferSizeInBytes(4 * 1024)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private val audioCallback = object: AudioDecoder.Callback{
        override fun onDecoded(buffer: ByteBuffer, index: Int, presentationTimeUs: Long) {
            if(!isRunning.get()) return
            mediaSync.queueAudio(buffer, index, presentationTimeUs)
        }
    }
    private val audioDecoder = AudioDecoder(audioCallback)
    private val videoDecoder = VideoDecoder(width, height, syncSurface)

    private var isRunning = AtomicBoolean(false)

    init {
        mediaSync.setAudioTrack(audioTrack)
        audioDecoder.configure()
        videoDecoder.configure()
        videoDecoder.setScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
    }

    fun start() {
        isRunning.set(true)
        mediaSync.playbackParams = PlaybackParams().setSpeed(1f)
        audioDecoder.start()
        videoDecoder.start()
    }

    fun stop() {
        Log.d(TAG, "stop()")
        if(!isRunning.getAndSet(false)){
            return
        }
        mediaSync.flush()
        audioDecoder.stop()
        videoDecoder.stop()
    }

    fun release() {
        Log.d(TAG, "release()")
        mediaSync.release()
        audioDecoder.release()
        videoDecoder.release()
        audioTrack.release()
        handlerThread.quitSafely()
    }

    fun enqueueAudioData(data: ByteArray, presentationTimeUs: Long) {
        if (!isRunning.get()) return
        audioDecoder.enqueue(Sample(data, presentationTimeUs))
    }

    fun enqueueVideoData(data: ByteArray, presentationTimeUs: Long) {
        if (!isRunning.get()) return
        videoDecoder.enqueue(Sample(data, presentationTimeUs))
    }

    fun configureAudioCodec(codecSpecificData: ByteArray){
        if(!isRunning.get()) return
        audioDecoder.enqueue(Sample(codecSpecificData, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG))
    }
    fun configureVideoCodec(codecSpecificData: ByteArray){
        if(!isRunning.get()) return
        videoDecoder.enqueue(Sample(codecSpecificData, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG))
    }
}
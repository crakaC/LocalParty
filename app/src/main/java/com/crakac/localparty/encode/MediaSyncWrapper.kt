package com.crakac.localparty.encode

import android.media.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean

class MediaSyncWrapper(
    outputSurface: Surface,
    width: Int,
    height: Int
) {
    companion object {
        private val TAG = MediaSyncWrapper::class.java.simpleName
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_COUNT = 2
    }

    private val mediaSyncCallback = object : MediaSync.Callback() {
        override fun onAudioBufferConsumed(
            sync: MediaSync,
            audioBuffer: ByteBuffer,
            bufferId: Int
        ) {
            if (!isRunning.get()) return
            runCatching {
                audioDecoder.releaseOutputBuffer(bufferId, false)
            }
        }
    }

    private val handlerThread = HandlerThread("MediaSyncHandler").apply { start() }
    private val sync = MediaSync().apply {
        setSurface(outputSurface)
        setCallback(mediaSyncCallback, Handler(handlerThread.looper))
    }

    private val syncSurface = sync.createInputSurface()

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

    private val audioHandlerThread = HandlerThread("AudioDecoder").apply { start() }
    private val audioCallback: MediaCodec.Callback =
        object : DefaultMediaCodecCallback("AudioDecoderCallback") {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                try {
                    if (!isRunning.get()) return
                    val buffer = codec.getInputBuffer(index) ?: return
                    while (isRunning.get() && audioQueue.isEmpty()) {
                        Thread.sleep(10)
                    }
                    val (data, presentationTimeUs) = audioQueue.poll() ?: return
                    buffer.put(data, 0, data.size)
                    codec.queueInputBuffer(index, 0, data.size, presentationTimeUs, 0)
                } catch (e: Exception) {
                    Log.w(TAG, e.stackTraceToString())
                }
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo
            ) {
                if (!isRunning.get()) {
                    return
                }
                try {
                    val audioBuffer: ByteBuffer = codec.getOutputBufferOrThrow(index)
                    sync.queueAudio(audioBuffer, index, info.presentationTimeUs)
                    // audioBuffer will be released at MediaSync.Callback#onAudioBufferConsumed()
                    // Need not to call codec.releaseOutputBuffer() here.
                } catch (e: Exception) {
                    Log.w(TAG, e.stackTraceToString())
                }
            }
        }
    private val audioDecoder =
        MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            setCallback(audioCallback, Handler(audioHandlerThread.looper))
        }
    private val audioFormat =
        MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNEL_COUNT)
            .apply {
                setByteBuffer(
                    "csd-0",
                    CSD.create(
                        MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                        CSD.Sample44100,
                        CSD.ChannelStereo
                    )
                )
            }

    private val videoHandlerThread = HandlerThread("VideoDecoder").apply { start() }
    private val videoCallback: MediaCodec.Callback =
        object : DefaultMediaCodecCallback("VideoDecoderCallback") {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                if (!isRunning.get()) return
                try {
                    val buffer = codec.getInputBuffer(index) ?: return
                    while (isRunning.get() && videoQueue.isEmpty()) {
                        Thread.sleep(10)
                    }
                    val (data, presentationTimeUs) = videoQueue.poll() ?: return
                    buffer.put(data, 0, data.size)
                    codec.queueInputBuffer(index, 0, data.size, presentationTimeUs, 0)
                } catch (e: Exception) {
                    Log.w(TAG, e.stackTraceToString())
                }
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                if (!isRunning.get()) return
                try {
                    // surface timestamp must contain media presentation time in nanoseconds.
                    codec.releaseOutputBuffer(index, info.presentationTimeUs * 1000)
                } catch (e: Exception) {
                    Log.w(TAG, e.stackTraceToString())
                }
            }
        }
    private val videoDecoder =
        MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            setCallback(videoCallback, Handler(videoHandlerThread.looper))
        }
    private val videoFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)

    private val audioQueue = LinkedBlockingDeque<Sample>()
    private val videoQueue = LinkedBlockingDeque<Sample>()

    private var isRunning = AtomicBoolean(false)
    private val handlerThreads = arrayOf(handlerThread, audioHandlerThread, videoHandlerThread)

    init {
        sync.setAudioTrack(audioTrack)
        audioDecoder.configure(audioFormat, null, null, 0)
        videoDecoder.configure(videoFormat, syncSurface, null, 0)
        videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
    }

    fun start() {
        isRunning.set(true)
        sync.playbackParams = PlaybackParams().setSpeed(1f)
        audioDecoder.start()
        videoDecoder.start()
    }

    fun stop() {
        Log.d(TAG, "stop()")
        isRunning.set(false)
        sync.flush()
        audioQueue.clear()
        videoQueue.clear()
        audioDecoder.stop()
        videoDecoder.stop()
    }

    fun release() {
        Log.d(TAG, "release()")
        sync.release()
        audioDecoder.release()
        videoDecoder.release()
        audioTrack.release()
        handlerThreads.forEach { it.quitSafely() }
    }

    fun enqueueAudioData(data: ByteArray, presentationTimeUs: Long) {
        if (!isRunning.get()) return
        audioQueue.offer(Sample(data, presentationTimeUs))
    }

    fun enqueueVideoData(data: ByteArray, presentationTimeUs: Long) {
        if (!isRunning.get()) return
        videoQueue.offer(Sample(data, presentationTimeUs))
    }
}
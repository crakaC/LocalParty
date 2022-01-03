package com.crakac.localparty.encode

import android.media.*
import android.media.MediaSync
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

class MediaSync(
    outputSurface: Surface,
    width: Int,
    height: Int
) : MediaCodec.Callback() {

    companion object {
        private val TAG = MediaSync::class.simpleName
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_COUNT = 1
    }

    private val handlerThread = HandlerThread("MediaSync").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private val mediaSyncCallback = object : MediaSync.Callback() {
        override fun onAudioBufferConsumed(
            sync: MediaSync,
            audioBuffer: ByteBuffer,
            bufferId: Int
        ) {
            audioDecoder.releaseOutputBuffer(bufferId, false)
        }
    }

    private val sync = MediaSync().apply {
        setSurface(outputSurface)
        setCallback(mediaSyncCallback, handler)
    }

    private val syncSurface = sync.createInputSurface()

    private val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .build()
        )
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .setBufferSizeInBytes(4 * 1024)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private val audioDecoder =
        MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            setCallback(
                this@MediaSync,
                Handler(HandlerThread("AudioHandler").apply { start() }.looper)
            )
        }
    private val audioFormat =
        MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNEL_COUNT)
            .apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(byteArrayOf(0x11.toByte(), 0x90.toByte())))
            }

    private val audioQueue = LinkedBlockingDeque<Sample>()
    private val videoQueue = LinkedBlockingDeque<Sample>()

    private val videoDecoder =
        MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            setCallback(
                this@MediaSync,
                Handler(HandlerThread("VideoHandler").apply { start() }.looper)
            )
        }
    private val videoFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)

    init {
        sync.setAudioTrack(audioTrack)
        audioDecoder.configure(audioFormat, null, null, 0)
        videoDecoder.configure(videoFormat, syncSurface, null, 0)
    }

    fun start() {
        sync.playbackParams = PlaybackParams().setSpeed(1f)
        audioDecoder.start()
        videoDecoder.start()
    }

    fun stop() {
        sync.playbackParams = PlaybackParams().setSpeed(0f)
        audioDecoder.stop()
        videoDecoder.stop()
    }

    fun release() {
        sync.release()
        audioDecoder.release()
        videoDecoder.release()
        handlerThread.quitSafely()
    }

    fun enqueueAudioData(data: ByteArray, presentationTimeUs: Long) {
        audioQueue.offer(Sample(data, presentationTimeUs))
    }

    fun enqueueVideoData(data: ByteArray, presentationTimeUs: Long) {
        videoQueue.offer(Sample(data, presentationTimeUs))
    }

    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
        try {
            if (codec == audioDecoder) {
                val buffer =
                    codec.getInputBufferOrThrow(index)
                val (data, presentationTimeUs) = audioQueue.poll(
                    Long.MAX_VALUE,
                    TimeUnit.MILLISECONDS
                )
                buffer.put(data, 0, data.size)
                codec.queueInputBuffer(index, 0, data.size, presentationTimeUs, 0)
            } else {
                val buffer = codec.getInputBufferOrThrow(index)
                val (data, presentationTimeUs) = videoQueue.poll(
                    Long.MAX_VALUE,
                    TimeUnit.MILLISECONDS
                )
                buffer.put(data, 0, data.size)
                codec.queueInputBuffer(index, 0, data.size, presentationTimeUs, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, e.stackTraceToString())
        }
    }

    override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
        try {
            if (codec == videoDecoder) {
                // surface timestamp must contain media presentation time in nanoseconds.
                codec.releaseOutputBuffer(index, info.presentationTimeUs * 1000)
            } else {
                val audioBuffer =
                    codec.getOutputBufferOrThrow(index)
                sync.queueAudio(audioBuffer, index, info.presentationTimeUs)
            }
        } catch (e: Exception) {
            Log.w(TAG, e.stackTraceToString())
        }
    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        Log.w(TAG, e.stackTraceToString())
    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        Log.d(TAG, "${codec.name} format changed: $format")
    }
}
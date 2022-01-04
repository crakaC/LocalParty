package com.crakac.localparty.encode

import android.annotation.SuppressLint
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val NANOS_PER_SECOND = 1_000_000_000L
private const val NANOS_PER_MICROS = 1_000L
private const val MIME_TYPE_AAC = MediaFormat.MIMETYPE_AUDIO_AAC
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
private const val CHANNEL_COUNT = 2
private const val BYTES_PER_FRAME = 2 * CHANNEL_COUNT
private const val SAMPLE_RATE = 44_100
private const val BIT_RATE = CHANNEL_COUNT * 64 * 1024 // 64kbps per channel
private const val TAG = "AudioEncoder"

class AudioEncoder(private val callback: Encoder.Callback) : Encoder {
    private val readScope = createSingleThreadScope("read")
    private val writeScope = createSingleThreadScope("write")
    private var readJob: Job? = null
    private var writeJob: Job? = null

    private val isRunning = AtomicBoolean(false)

    override val encoderType = Encoder.Type.Audio
    private val audioBufferSizeInBytes = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AudioFormat.ENCODING_PCM_16BIT
    )

    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord.Builder()
        .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(CHANNEL_CONFIG)
                .build()
        )
        .setBufferSizeInBytes(audioBufferSizeInBytes)
        .build()

    private val echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)

    private val audioBuffer = ByteArray(audioBufferSizeInBytes)

    private val format: MediaFormat =
        MediaFormat.createAudioFormat(MIME_TYPE_AAC, SAMPLE_RATE, CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSizeInBytes)
        }

    private val codec = MediaCodec.createEncoderByType(MIME_TYPE_AAC)

    // https://github.com/google/mediapipe/blob/master/mediapipe/java/com/google/mediapipe/components/MicrophoneHelper.java
    private fun getTimestamp(framePosition: Long): Long {
        val audioTimestamp = AudioTimestamp()
        audioRecord.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC)
        val referenceFrame = audioTimestamp.framePosition
        val referenceTimestamp = audioTimestamp.nanoTime
        val timestampNanos =
            referenceTimestamp + (framePosition - referenceFrame) * NANOS_PER_SECOND / SAMPLE_RATE
        return timestampNanos / NANOS_PER_MICROS
    }

    override fun prepare() {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    override fun start() {
        if(isRunning.get()){
            Log.d(TAG, "already started")
            return
        }
        isRunning.set(true)

        echoCanceler?.enabled = true
        audioRecord.startRecording()
        codec.start()

        readJob = read()
        writeJob = write()
    }

    private fun read() = readScope.launch {
        var totalNumFramesRead = 0L
        while (isActive && isRunning.get()) {
            synchronized(readScope) {
                val readBytes = audioRecord.read(audioBuffer, 0, audioBufferSizeInBytes)
                val bufferIndex = codec.dequeueInputBuffer(1000L)
                if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) return@synchronized
                val inputBuffer = codec.getInputBuffer(bufferIndex) ?: return@synchronized
                inputBuffer.clear()
                inputBuffer.put(audioBuffer)
                codec.queueInputBuffer(
                    bufferIndex, 0, readBytes, getTimestamp(totalNumFramesRead), 0
                )
                totalNumFramesRead += readBytes / BYTES_PER_FRAME
            }
        }
    }

    private fun write() = writeScope.launch {
        val bufferInfo = MediaCodec.BufferInfo()
        while (isActive && isRunning.get()) {
            synchronized(writeScope) {
                val bufferIndex = codec.dequeueOutputBuffer(bufferInfo, 1000L)
                if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) return@synchronized
                if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    callback.onFormatChanged(codec.outputFormat, encoderType)
                    return@synchronized
                }

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size > 0) {
                    val outputBuffer = codec.getOutputBuffer(bufferIndex) ?:return@synchronized
                    outputBuffer
                        .position(bufferInfo.offset)
                        .limit(bufferInfo.offset + bufferInfo.size)
                    outputBuffer.get(audioBuffer, 0, bufferInfo.size)
                    callback.onEncoded(outputBuffer, bufferInfo, encoderType)
                    codec.releaseOutputBuffer(bufferIndex, false)
                }

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    Log.d(TAG, "End of stream.")
                    return@launch
                }
            }
        }
    }

    override fun stop() {
        if(!isRunning.get()){
            Log.d(TAG, "not running")
            return
        }
        isRunning.set(false)

        readJob?.cancel()
        readJob = null
        writeJob?.cancel()
        writeJob = null

        audioRecord.stop()

        synchronized(readScope) {
            synchronized(writeScope) {
                codec.stop()
            }
        }
    }

    override fun release() {
        audioRecord.release()
        codec.release()
        readScope.cancel()
        writeScope.cancel()
    }
}
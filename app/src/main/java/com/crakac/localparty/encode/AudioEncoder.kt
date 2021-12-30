package com.crakac.localparty.encode

import android.annotation.SuppressLint
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val NANOS_PER_SECOND = 1_000_000_000L
private const val NANOS_PER_MICROS = 1_000L
private const val MIME_TYPE_AAC = MediaFormat.MIMETYPE_AUDIO_AAC
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val CHANNEL_COUNT = 1
private const val BYTES_PER_FRAME = 2 * CHANNEL_COUNT
private const val SAMPLE_RATE = 44_100
private const val BIT_RATE = 64 * 1024 // 64kbps
private const val TAG = "AudioEncoder"

class AudioEncoder(private val callback: Encoder.Callback) : Encoder {
    private val readScope = Codec.createSingleThreadScope("read")
    private val writeScope = Codec.createSingleThreadScope("write")
    private var readJob: Job? = null
    private var writeJob: Job? = null

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
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSizeInBytes)
        }

    private val codec = MediaCodec.createEncoderByType(MIME_TYPE_AAC)

    // https://github.com/google/mediapipe/blob/master/mediapipe/java/com/google/mediapipe/components/MicrophoneHelper.java
    private val audioTimestamp = AudioTimestamp()
    private fun getTimestamp(framePosition: Long): Long {
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
        echoCanceler?.enabled = true
        audioRecord.startRecording()
        codec.start()

        readJob?.cancel()
        readJob = read()

        writeJob?.cancel()
        writeJob = write()
    }

    private fun read() = readScope.launch {
        var totalNumFramesRead = 0L
        while (isActive) {
            val readBytes = audioRecord.read(audioBuffer, 0, audioBufferSizeInBytes)
            val bufferIndex = codec.dequeueInputBuffer(-1)

            val inputBuffer =
                codec.getInputBuffer(bufferIndex) ?: throw RuntimeException("Input buffer is null")
            inputBuffer.clear()
            inputBuffer.put(audioBuffer)
            codec.queueInputBuffer(
                bufferIndex, 0, readBytes, getTimestamp(totalNumFramesRead), 0
            )
            totalNumFramesRead += readBytes / BYTES_PER_FRAME
        }
    }

    private fun write() = writeScope.launch {
        val bufferInfo = MediaCodec.BufferInfo()
        while (isActive) {
            val bufferIndex = codec.dequeueOutputBuffer(bufferInfo, 1000L)
            if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) continue
            if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                callback.onFormatChanged(codec.outputFormat, encoderType)
                continue
            }

            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                bufferInfo.size = 0
            }

            if (bufferInfo.size > 0) {
                val outputBuffer =
                    codec.getOutputBuffer(bufferIndex)
                        ?: throw RuntimeException("Output buffer is null")
                outputBuffer
                    .position(bufferInfo.offset)
                    .limit(bufferInfo.offset + bufferInfo.size)
                outputBuffer.get(audioBuffer, 0, bufferInfo.size)
                callback.onEncoded(outputBuffer, bufferInfo, encoderType)
                codec.releaseOutputBuffer(bufferIndex, false)
            }

            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                Log.d(TAG, "End of stream.")
                break
            }
        }
    }

    override fun stop() {
        audioRecord.stop()
        codec.stop()
    }

    override fun release() {
        audioRecord.release()
        codec.release()
    }
}
package com.crakac.localparty.encode

import android.annotation.SuppressLint
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import java.nio.ByteBuffer

class AsyncAudioEncoder(private val callback: Encoder.Callback) :
    AsyncCodec(mime = MediaFormat.MIMETYPE_AUDIO_AAC, isEncoder = true) {
    companion object {
        private val TAG = AsyncAudioEncoder::class.java.simpleName
    }

    private val audioBufferSizeInBytes = AudioRecord.getMinBufferSize(
        AudioEncoderConfig.SAMPLE_RATE,
        AudioEncoderConfig.CHANNEL_CONFIG,
        AudioFormat.ENCODING_PCM_16BIT
    )

    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord.Builder()
        .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(AudioEncoderConfig.SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioEncoderConfig.CHANNEL_CONFIG)
                .build()
        )
        .setBufferSizeInBytes(audioBufferSizeInBytes)
        .build()

    private val echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)
    private val audioBuffer = ByteArray(audioBufferSizeInBytes)

    override val format: MediaFormat =
        MediaFormat.createAudioFormat(
            AudioEncoderConfig.MIME_TYPE_AAC,
            AudioEncoderConfig.SAMPLE_RATE,
            AudioEncoderConfig.CHANNEL_COUNT
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AudioEncoderConfig.BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSizeInBytes)
        }

    private var totalNumFramesRead = 0L

    // https://github.com/google/mediapipe/blob/master/mediapipe/java/com/google/mediapipe/components/MicrophoneHelper.java
    private fun getTimestamp(framePosition: Long): Long {
        val audioTimestamp = AudioTimestamp()
        audioRecord.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC)
        val referenceFrame = audioTimestamp.framePosition
        val referenceTimestamp = audioTimestamp.nanoTime
        val timestampNanos =
            referenceTimestamp + (framePosition - referenceFrame) * AudioEncoderConfig.NANOS_PER_SECOND / AudioEncoderConfig.SAMPLE_RATE
        return timestampNanos / AudioEncoderConfig.NANOS_PER_MICROS
    }

    override fun onCodecInputBufferAvailable(codec: MediaCodec, index: Int) {
        val inputBuffer = codec.getInputBuffer(index) ?: return
        val readBytes = audioRecord.read(audioBuffer, 0, audioBufferSizeInBytes)
        inputBuffer.put(audioBuffer)
        codec.queueInputBuffer(index, 0, readBytes, getTimestamp(totalNumFramesRead), 0)
        totalNumFramesRead += readBytes / AudioEncoderConfig.BYTES_PER_FRAME
    }

    override fun onCodecOutputBufferAvailable(
        codec: MediaCodec,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        index: Int
    ) {
        callback.onEncoded(buffer, info, Encoder.Type.Audio)
        codec.releaseOutputBuffer(index, false)
    }

    override fun onStart() {
        echoCanceler?.enabled = true
        audioRecord.startRecording()
        totalNumFramesRead = 0L
    }

    override fun onRequestEOS() {
        audioRecord.stop()
    }

    override fun onCodecOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        callback.onFormatChanged(format, Encoder.Type.Audio)
    }
}
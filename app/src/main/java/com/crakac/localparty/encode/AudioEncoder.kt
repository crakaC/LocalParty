package com.crakac.localparty.encode

import android.annotation.SuppressLint
import android.media.*
import android.util.Log

private const val MIME_TYPE_AAC = MediaFormat.MIMETYPE_AUDIO_AAC
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val CHANNEL_COUNT = 1
private const val SAMPLE_RATE = 44_100
private const val BIT_RATE = 64 * 1024 // 64kbps
private const val TAG = "AudioEncoder"

class AudioEncoder(private val callback: Encoder.Callback) : Encoder,
    MediaCodec.Callback() {
    override val encoderType = Encoder.Type.Audio
    private val audioBufferSizeInBytes = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AudioFormat.ENCODING_PCM_16BIT
    )

    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.CAMCORDER,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AudioFormat.ENCODING_PCM_16BIT,
        audioBufferSizeInBytes
    )

    private val audioBuffer = ByteArray(audioBufferSizeInBytes)

    private val format: MediaFormat =
        MediaFormat.createAudioFormat(MIME_TYPE_AAC, SAMPLE_RATE, CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSizeInBytes)
        }

    private val codec = MediaCodec.createEncoderByType(MIME_TYPE_AAC).apply {
        setCallback(this@AudioEncoder)
    }

    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
        val readBytes = audioRecord.read(audioBuffer, 0, audioBufferSizeInBytes)
        val inputBuffer =
            codec.getInputBuffer(index) ?: throw RuntimeException("Input buffer is null")
        inputBuffer.put(audioBuffer)
        codec.queueInputBuffer(
            index, 0, readBytes, System.nanoTime() / 1000L, 0
        )
    }

    override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
            codec.stop()
            return
        }
        val outputBuffer =
            codec.getOutputBuffer(index) ?: throw RuntimeException("Output buffer is null")
        callback.onEncoded(outputBuffer, info, encoderType)
        codec.releaseOutputBuffer(index, false)
    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        Log.e(TAG, e.toString())
    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        callback.onFormatChanged(format, encoderType)
    }

    override fun prepare() {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    override fun start() {
        audioRecord.startRecording()
        codec.start()
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
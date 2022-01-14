package com.crakac.localparty.media

import android.annotation.SuppressLint
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import com.crakac.localparty.media.AudioEncoderConfig.Companion.BIT_RATE
import com.crakac.localparty.media.AudioEncoderConfig.Companion.BYTES_PER_FRAME
import com.crakac.localparty.media.AudioEncoderConfig.Companion.CHANNEL_CONFIG
import com.crakac.localparty.media.AudioEncoderConfig.Companion.CHANNEL_COUNT
import com.crakac.localparty.media.AudioEncoderConfig.Companion.MIME_TYPE_AAC
import com.crakac.localparty.media.AudioEncoderConfig.Companion.SAMPLE_RATE
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

class AudioEncoder(private val callback: Encoder.Callback) {
    companion object {
        private val TAG = AudioEncoder::class.java.simpleName
    }

    private val readScope = createSingleThreadScope("read")
    private val writeScope = createSingleThreadScope("write")
    private var readJob: Job? = null
    private var writeJob: Job? = null

    var isRunning: Boolean = false
        private set

    private val type = Encoder.Type.Audio
    private val sampleRate = SAMPLE_RATE
    private val audioBufferSizeInBytes = AudioRecord.getMinBufferSize(
        sampleRate,
        CHANNEL_CONFIG,
        AudioFormat.ENCODING_PCM_16BIT
    )

    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord.Builder()
        .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(CHANNEL_CONFIG)
                .build()
        )
        .setBufferSizeInBytes(audioBufferSizeInBytes)
        .build()

    private val echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)

    private val audioBuffer = ByteArray(audioBufferSizeInBytes)

    private val format: MediaFormat =
        MediaFormat.createAudioFormat(MIME_TYPE_AAC, sampleRate, CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSizeInBytes)
        }

    private val codec = MediaCodec.createEncoderByType(MIME_TYPE_AAC)

    fun configure() {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    fun start() {
        if (isRunning) {
            Log.d(TAG, "already started")
            return
        }
        isRunning = true

        echoCanceler?.enabled = true
        audioRecord.startRecording()
        codec.start()

        readJob = read()
        writeJob = write()
    }

    private fun read() = readScope.launch {
        var totalNumFramesRead = 0L
        while (isActive && isRunning) {
            val readBytes = audioRecord.read(audioBuffer, 0, audioBufferSizeInBytes)
            val bufferIndex = codec.dequeueInputBuffer(1000L)
            if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) continue
            val inputBuffer = codec.getInputBuffer(bufferIndex) ?: continue
            inputBuffer.clear()
            inputBuffer.put(audioBuffer)
            codec.queueInputBuffer(
                bufferIndex,
                0,
                readBytes,
                audioRecord.getTimestampMicros(totalNumFramesRead, sampleRate),
                0
            )
            totalNumFramesRead += readBytes / BYTES_PER_FRAME
        }
    }

    private fun write() = writeScope.launch {
        val bufferInfo = MediaCodec.BufferInfo()
        while (isActive && isRunning) {

            val bufferIndex = codec.dequeueOutputBuffer(bufferInfo, 1000L)
            if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) continue

            if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                callback.onFormatChanged(codec.outputFormat, type)
            }

            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                val outputBuffer = codec.getOutputBuffer(bufferIndex) ?: continue
                val csd = ByteArray(bufferInfo.size)
                outputBuffer
                    .position(bufferInfo.offset)
                    .limit(bufferInfo.offset + bufferInfo.size)
                outputBuffer.get(csd)
                callback.onCSD(csd, type)
                codec.releaseOutputBuffer(bufferIndex, false)
                continue
            }

            if (bufferInfo.size > 0) {
                val outputBuffer = codec.getOutputBuffer(bufferIndex) ?: continue
                outputBuffer
                    .position(bufferInfo.offset)
                    .limit(bufferInfo.offset + bufferInfo.size)
                outputBuffer.get(audioBuffer, 0, bufferInfo.size)
                callback.onEncoded(outputBuffer, bufferInfo, type)
                codec.releaseOutputBuffer(bufferIndex, false)
            }

            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                Log.d(TAG, "End of stream.")
                return@launch
            }
        }
    }

    fun stop() {
        if (!isRunning) {
            Log.d(TAG, "not running")
            return
        }
        isRunning = false
        signalEOS()
        readJob?.cancel()
        readJob = null
        writeJob?.cancel()
        writeJob = null

        audioRecord.stop()
    }

    fun release() {
        audioRecord.release()
        codec.release()
        readScope.cancel()
        writeScope.cancel()
    }

    private fun signalEOS(){
        thread {
            val index = codec.dequeueInputBuffer(1_000_000L)
            if (index < 0) return@thread
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
    }
}
package com.crakac.localparty.encode

import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque

private val TAG = AACDecoder::class.simpleName

class AACDecoder : Decoder {
    private val readScope = Codec.createSingleThreadScope("read")
    private val writeScope = Codec.createSingleThreadScope("write")
    private var readJob: Job? = null
    private var writeJob: Job? = null

    override val decoderType = Decoder.Type.Audio

    private val decodeBuffer = ByteArray(16 * 1024)

    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)

    private val queue = LinkedBlockingDeque<Sample>()

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
                .setSampleRate(44_100)
                .build()
        )
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    fun put(buffer: ByteBuffer, size: Int, presentationTimeUs: Long) {
        val data = ByteArray(size)
        buffer.get(data)
        queue.offer(Sample(data, presentationTimeUs))
    }

    fun configure(format: MediaFormat) {
        codec.configure(format, null, null, 0)
    }

    override fun prepare() {
    }

    override fun start() {
        codec.start()
        audioTrack.play()

        readJob?.cancel()
        readJob = read()

        writeJob?.cancel()
        writeJob = write()
    }

    private fun read() = readScope.launch {
        while (isActive) {
            if (queue.isEmpty()) {
                delay(1L)
                continue
            }
            val bufferIndex = codec.dequeueInputBuffer(-1)
            if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) continue
            val (data, presentationTimeUs) = queue.poll() ?: continue
            val inputBuffer =
                codec.getInputBuffer(bufferIndex) ?: throw RuntimeException("Input buffer is null")
            inputBuffer.clear()
            inputBuffer.put(data)
            codec.queueInputBuffer(bufferIndex, 0, data.size, presentationTimeUs, 0)
        }
    }

    private fun write() = writeScope.launch {
        val bufferInfo = MediaCodec.BufferInfo()
        while (isActive) {
            val bufferIndex = codec.dequeueOutputBuffer(bufferInfo, 1000L)
            if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) continue
            if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue

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
                outputBuffer.get(decodeBuffer, 0, bufferInfo.size)
                audioTrack.write(decodeBuffer, 0, bufferInfo.size)
                codec.releaseOutputBuffer(bufferIndex, false)
            }

            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                Log.d(TAG, "End of stream.")
                break
            }
        }
    }

    override fun stop() {
        readJob?.cancel()
        writeJob?.cancel()
        codec.stop()
        audioTrack.stop()
    }

    override fun release() {
        readScope.cancel()
        writeScope.cancel()
        codec.release()
        audioTrack.release()
    }

    private data class Sample(val data: ByteArray, val presentationTimeUs: Long) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Sample

            if (presentationTimeUs != other.presentationTimeUs) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + presentationTimeUs.hashCode()
            return result
        }
    }
}
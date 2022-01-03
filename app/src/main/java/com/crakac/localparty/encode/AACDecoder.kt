package com.crakac.localparty.encode

import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque

private val TAG = AACDecoder::class.simpleName

class AACDecoder : Decoder {
    private val readScope = createSingleThreadScope("read")
    private val writeScope = createSingleThreadScope("write")
    private var readJob: Job? = null
    private var writeJob: Job? = null

    override val decoderType = Decoder.Type.Audio
    private val decodeBuffer = ByteArray(16 * 1024)

    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val format =
        MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(byteArrayOf(0x11.toByte(), 0x90.toByte())))
        }

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
        .setBufferSizeInBytes(4 * 1024)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    override fun enqueue(data: ByteArray, presentationTimeUs: Long) {
        queue.offer(Sample(data, presentationTimeUs))
    }

    override fun prepare() {
        codec.configure(format, null, null, 0)
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

    fun releaseBuffer(bufferId: Int) {
        codec.releaseOutputBuffer(bufferId, false)
    }
}
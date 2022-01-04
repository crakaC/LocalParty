package com.crakac.localparty.encode

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.FileDescriptor
import java.nio.ByteBuffer


private const val NUM_TRACKS = 2 // video, audio
private const val UNINITIALIZED = -1

class MyMediaRecorder(
    fileDescriptor: FileDescriptor,
    width: Int,
    height: Int,
    private val callback: RecorderCallback
) : Encoder.Callback {

    fun interface RecorderCallback {
        fun onRecorded(data: ByteArray, presentationTimeUs: Long, type: Encoder.Type)
    }

    private val muxer = MediaMuxer(fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    /**
     * Input surface of video encoder.
     *
     * Before using this surface, you must call [prepare()].
     */
    val inputSurface: Surface
        get() = videoEncoder.inputSurface

    private val videoEncoder = VideoEncoder(width, height, this)
    private val audioEncoder = AudioEncoder(this)

    private var isMuxerAvailable = false
    private var trackCount = 0
    private val trackIds = MutableList(NUM_TRACKS) { UNINITIALIZED }

    override fun onFormatChanged(encoder: Encoder, format: MediaFormat) {
        val index = encoder.type.ordinal
        synchronized(this) {
            if (trackIds[index] != UNINITIALIZED) return
            trackIds[index] = muxer.addTrack(format)
            trackCount++
            if (trackCount == NUM_TRACKS) {
                muxer.start()
                isMuxerAvailable = true
            }
        }
    }

    override fun onEncoded(encoder: Encoder, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!isMuxerAvailable) return
        val trackId = trackIds[encoder.type.ordinal]
        muxer.writeSampleData(trackId, buffer, info)

        val byteArray = ByteArray(info.size)
        buffer.position(info.offset).limit(info.offset + info.size)
        buffer.get(byteArray)
        callback.onRecorded(byteArray, info.presentationTimeUs, encoder.type)
    }

    fun prepare() {
        isMuxerAvailable = false
        trackIds.replaceAll { UNINITIALIZED }
        videoEncoder.prepare()
        audioEncoder.prepare()
    }

    fun start() {
        videoEncoder.start()
        audioEncoder.start()
    }

    fun stop() {
        videoEncoder.stop()
        audioEncoder.stop()
        muxer.stop()
    }

    fun release() {
        videoEncoder.release()
        audioEncoder.release()
    }
}
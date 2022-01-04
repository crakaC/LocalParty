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

    val surface: Surface
        get() = videoEncoder.inputSurface

    private val videoEncoder = VideoEncoder(width, height, this)
    private val audioEncoder = AudioEncoder(this)

    private var isMuxerAvailable = false
    private var trackCount = 0
    private val trackIds = MutableList(NUM_TRACKS) { UNINITIALIZED }

    override fun onFormatChanged(format: MediaFormat, type: Encoder.Type) {
        val index = type.ordinal
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

    override fun onEncoded(buffer: ByteBuffer, info: MediaCodec.BufferInfo, type: Encoder.Type) {
        if (!isMuxerAvailable) return
        val trackId = trackIds[type.ordinal]
        muxer.writeSampleData(trackId, buffer, info)

        val byteArray = ByteArray(info.size)
        buffer.position(info.offset).limit(info.offset + info.size)
        buffer.get(byteArray)
        callback.onRecorded(byteArray, info.presentationTimeUs, type)
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
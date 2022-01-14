package com.crakac.localparty.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.FileDescriptor
import java.nio.ByteBuffer
import kotlin.concurrent.thread


private const val NUM_TRACKS = 2 // video, audio
private const val UNINITIALIZED = -1

class MyMediaRecorder(
    fileDescriptor: FileDescriptor,
    width: Int,
    height: Int,
    private val callback: MediaEncoder.MediaEncoderCallback
) : Encoder.Callback {

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

    override fun onCSD(csd: ByteArray, type: Encoder.Type) {
        callback.onCodecSpecificData(csd, type)
    }

    override fun onEncoded(buffer: ByteBuffer, info: MediaCodec.BufferInfo, type: Encoder.Type) {
        if (!isMuxerAvailable) return
        val trackId = trackIds[type.ordinal]
        muxer.writeSampleData(trackId, buffer, info)

        val byteArray = ByteArray(info.size)
        buffer.position(info.offset).limit(info.offset + info.size)
        buffer.get(byteArray)
        callback.onEncoded(byteArray, info.presentationTimeUs, type)
    }

    fun prepare() {
        isMuxerAvailable = false
        trackIds.replaceAll { UNINITIALIZED }
        videoEncoder.configure()
        audioEncoder.configure()
    }

    fun start() {
        videoEncoder.start()
        audioEncoder.start()
    }

    fun stop() {
        videoEncoder.stop()
        audioEncoder.stop()
        thread{
            while(videoEncoder.isRunning || audioEncoder.isRunning){
                Thread.sleep(50)
            }
            muxer.stop()
        }
    }

    fun release() {
        videoEncoder.release()
        audioEncoder.release()
    }
}
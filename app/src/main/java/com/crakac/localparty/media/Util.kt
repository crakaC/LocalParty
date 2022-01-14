package com.crakac.localparty.media

import android.media.AudioRecord
import android.media.AudioTimestamp
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors


// https://github.com/google/mediapipe/blob/master/mediapipe/java/com/google/mediapipe/components/MicrophoneHelper.java
const val NANOS_PER_SECOND = 1_000_000_000L
const val NANOS_PER_MICROS = 1_000L
fun AudioRecord.getTimestampMicros(framePosition: Long, sampleRate: Int): Long {
    val audioTimestamp = AudioTimestamp()
    getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC)
    val referenceFrame = audioTimestamp.framePosition
    val referenceTimestamp = audioTimestamp.nanoTime
    val timestampNanos =
        referenceTimestamp + (framePosition - referenceFrame) * NANOS_PER_SECOND / sampleRate
    return timestampNanos / NANOS_PER_MICROS
}

fun createSingleThreadScope(name: String) = CoroutineScope(
    Executors.newSingleThreadExecutor().asCoroutineDispatcher() +
            CoroutineName(name) +
            CoroutineExceptionHandler { _, t ->
                Log.w(name, t.stackTraceToString())
            }
)

fun Int.toByteArray(): ByteArray {
    return ByteArray(4) {
        (this shr (3 - it) * 8).toByte()
    }
}

fun ByteArray.toInt(): Int {
    assert(size == 4)
    return fold(0) { acc, byte ->
        (acc shl 8) + (byte.toInt() and 0xFF)
    }
}

fun Long.toByteArray() = ByteArray(8) {
    (this shr (7 - it) * 8).toByte()
}

fun ByteArray.toLong(): Long {
    assert(size == 8)
    return fold(0L) { acc, byte ->
        (acc shl 8) + (byte.toLong() and 0xFF)
    }
}
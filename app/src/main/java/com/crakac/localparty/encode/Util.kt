package com.crakac.localparty.encode

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

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
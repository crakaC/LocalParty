package com.crakac.localparty

import com.crakac.localparty.encode.toByteArray
import com.crakac.localparty.encode.toInt
import com.crakac.localparty.encode.toLong
import org.junit.Assert
import org.junit.Test
import java.nio.ByteBuffer

class BytesConversionTest {

    companion object {
        const val SizeOfInt = 4
        const val SizeOfLong = 8
    }
    @Test
    fun intToBytesConversionSameAsByteBuffer() {
        val byteBuffer = ByteBuffer.wrap(ByteArray(SizeOfInt))
        for (i in 0 until SizeOfInt) {
            val value = 1.shl(i * 8)
            byteBuffer.clear()
            byteBuffer.putInt(value)
            Assert.assertTrue(byteBuffer.array().contentEquals(value.toByteArray()))
        }
    }

    @Test
    fun longToBytesConversionSameAsByteBuffer() {
        val byteBuffer = ByteBuffer.wrap(ByteArray(SizeOfLong))
        for (i in 0 until SizeOfLong) {
            val value = 1L.shl(i * 8)
            byteBuffer.clear()
            byteBuffer.putLong(value)
            Assert.assertTrue(byteBuffer.array().contentEquals(value.toByteArray()))
        }
    }

    @Test
    fun restoreCollectInt(){
        for (i in 0 until SizeOfInt) {
            val value = 1.shl(i * 8)
            Assert.assertEquals(value, value.toByteArray().toInt())
        }
    }

    @Test
    fun restoreCollectLong(){
        for (i in 0 until SizeOfLong) {
            val value = 1L.shl(i * 8)
            Assert.assertEquals(value, value.toByteArray().toLong())
        }
    }
}
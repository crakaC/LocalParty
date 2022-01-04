package com.crakac.localparty

import android.media.MediaCodecInfo
import com.crakac.localparty.encode.CSD
import org.junit.Assert
import org.junit.Test

class CSDTest {
    @Test
    fun test() {
        val csd = CSD.createByteArray(
            MediaCodecInfo.CodecProfileLevel.AACObjectLC,
            CSD.Sample44100,
            CSD.ChannelMono
        )
        println(csd.map{it.toInt().and(0xFF).toString(16)})
        Assert.assertTrue(csd.contentEquals(byteArrayOf(0x12.toByte(), 0x08.toByte())))
    }
}
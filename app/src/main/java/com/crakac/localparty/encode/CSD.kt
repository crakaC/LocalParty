package com.crakac.localparty.encode

import androidx.annotation.VisibleForTesting
import java.nio.ByteBuffer

class CSD private constructor() {
    companion object {

        const val Sample96k = 0b0
        const val Sample88200 = 0b1
        const val Sample64k = 0b10
        const val Sample48k = 0b11
        const val Sample44100 = 0b100

        const val ChannelMono = 0b01
        const val ChannelStereo = 0b10

        fun create(
            profile: Int,
            samplingRate: Int = Sample44100,
            channel: Int = ChannelMono
        ): ByteBuffer {
            return ByteBuffer.wrap(createByteArray(profile, samplingRate, channel))
        }

        @VisibleForTesting
        fun createByteArray(
            profile: Int,
            samplingRate: Int = Sample44100,
            channel: Int = ChannelMono
        ): ByteArray {
            val bytes = ByteArray(2)
            bytes[0] = (profile.shl(3) or samplingRate.shr(1)).toByte()
            bytes[1] = (samplingRate.and(0x01).shl(7) or channel.shl(3)).toByte()
            return bytes
        }
    }
}
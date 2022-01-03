package com.crakac.localparty.encode

interface Decoder : Codec {
    enum class Type { Video, Audio }

    val decoderType: Type

    fun enqueue(data: ByteArray, presentationTimeUs: Long)
}
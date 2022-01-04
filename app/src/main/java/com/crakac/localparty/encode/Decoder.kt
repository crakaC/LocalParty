package com.crakac.localparty.encode

interface Decoder : Codec {
    fun enqueue(data: ByteArray, presentationTimeUs: Long)
}
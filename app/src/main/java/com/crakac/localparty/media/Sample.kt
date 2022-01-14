package com.crakac.localparty.media

data class Sample(val data: ByteArray, val presentationTimeUs: Long, val flag: Int = 0) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Sample

        if (presentationTimeUs != other.presentationTimeUs) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + presentationTimeUs.hashCode()
        return result
    }
}
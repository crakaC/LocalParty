package com.crakac.localparty.encode

enum class ChunkType { Video, Audio, VideoCSD, AudioCSD }
data class Chunk(
    val type: ChunkType,
    val dataSize: Int,
    val presentationTimeUs: Long,
    val data: ByteArray,
) {
    val isPartial: Boolean
        get() = remain != 0
    var remain: Int = 0
        private set

    val actualSize: Int
        get() = HeaderSize + dataSize - remain

    companion object {
        /**
         * Size of fields of chunk.
         * sizeOf(type) + sizeOf(dataSize) + sizeOf(presentationTimeUs)
         */
        const val HeaderSize = Byte.SIZE_BYTES + Int.SIZE_BYTES + Long.SIZE_BYTES
        fun fromByteArray(src: ByteArray, offset: Int, limit: Int): Chunk {
            var index = offset
            val type = ChunkType.values()[src[index].toInt()]
            index += Byte.SIZE_BYTES

            val dataSize = src.sliceArray(index until index + Int.SIZE_BYTES).toInt()
            index += Int.SIZE_BYTES

            val presentationTimeUs = src.sliceArray(index until index + Long.SIZE_BYTES).toLong()
            index += Long.SIZE_BYTES

            val data = ByteArray(dataSize)
            val length = minOf(limit - index, dataSize)
            System.arraycopy(src, index, data, 0, length)

            val remain = dataSize - length
            val chunk = Chunk(type, dataSize, presentationTimeUs, data)
            chunk.remain = remain
            return chunk
        }
    }

    fun toByteArray(): ByteArray {
        val totalSize = HeaderSize + data.size
        val byteArray = ByteArray(totalSize)
        var index = 0
        byteArray[index++] = type.ordinal.toByte()
        System.arraycopy(data.size.toByteArray(), 0, byteArray, index, Int.SIZE_BYTES)
        index += Int.SIZE_BYTES
        System.arraycopy(presentationTimeUs.toByteArray(), 0, byteArray, index, Long.SIZE_BYTES)
        index += Long.SIZE_BYTES
        System.arraycopy(data, 0, byteArray, index, data.size)
        return byteArray
    }

    /**
     * @return size of appended bytes
     */
    fun append(src: ByteArray, offset: Int = 0, limit: Int): Int {
        val length = minOf(limit - offset, remain)
        System.arraycopy(src, offset, data, data.size - remain, length)
        remain -= length
        return length
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chunk

        if (type != other.type) return false
        if (dataSize != other.dataSize) return false
        if (presentationTimeUs != other.presentationTimeUs) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + dataSize
        result = 31 * result + presentationTimeUs.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

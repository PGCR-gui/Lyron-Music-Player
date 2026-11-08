package com.tolete.player.data

/** Cabeçalho do contêiner Sony DSF e parâmetros do fluxo DSD. */
data class DsfHeader(
    val channelCount: Int,
    val dsdSampleRateHz: Int,
    val sampleCountPerChannel: Long,
    val blockSizePerChannel: Int,
    val dataStartOffset: Long,
    val dataSize: Long
) {
    val pcmSampleRateHz: Int get() = dsdSampleRateHz / 8
    val bytesPerChannel: Long get() = sampleCountPerChannel / 8L +
        if (sampleCountPerChannel % 8L == 0L) 0L else 1L
    val durationUs: Long get() = sampleCountPerChannel * 1_000_000L / dsdSampleRateHz

    companion object {
        const val HEADER_SIZE = 92
        private const val DATA_START = 92L

        /** Retorna null se os chunks ou os parâmetros essenciais não forem válidos. */
        fun parse(bytes: ByteArray): DsfHeader? {
            if (bytes.size < HEADER_SIZE) return null
            if (!bytes.startsWithAscii(0, "DSD ") || bytes.u64(4) != 28L) return null
            val declaredFileSize = bytes.u64(12).takeIf { it >= HEADER_SIZE.toLong() } ?: return null
            if (!bytes.startsWithAscii(28, "fmt ") || bytes.u64(32) != 52L) return null
            if (bytes.u32(40) != 1L || bytes.u32(44) != 0L) return null

            val channels = bytes.u32(52).takeIf { it in 1L..8L }?.toInt() ?: return null
            val dsdRate = bytes.u32(56).takeIf {
                it in 8L..Int.MAX_VALUE.toLong() && it % 8L == 0L
            }?.toInt() ?: return null
            val sampleCount = bytes.u64(64).takeIf { it >= 0L } ?: return null
            val blockSize = bytes.u32(72).takeIf { it in 1L..1_048_576L }?.toInt() ?: return null
            if (!bytes.startsWithAscii(80, "data")) return null
            val dataChunkSize = bytes.u64(84).takeIf { it >= 12L } ?: return null
            if (dataChunkSize > declaredFileSize - 80L) return null
            val dataSize = dataChunkSize - 12L

            val effectiveSampleCount = if (sampleCount > 0L) {
                sampleCount
            } else {
                (dataSize / channels / blockSize * blockSize) * 8L
            }
            if (effectiveSampleCount <= 0L) return null

            val bytesPerChannel = effectiveSampleCount / 8L +
                if (effectiveSampleCount % 8L == 0L) 0L else 1L
            val blocksPerChannel = bytesPerChannel / blockSize +
                if (bytesPerChannel % blockSize == 0L) 0L else 1L
            val availableBlocks = dataSize / blockSize / channels
            if (blocksPerChannel > availableBlocks) return null

            return DsfHeader(
                channelCount = channels,
                dsdSampleRateHz = dsdRate,
                sampleCountPerChannel = effectiveSampleCount,
                blockSizePerChannel = blockSize,
                dataStartOffset = DATA_START,
                dataSize = dataSize
            )
        }

        private fun ByteArray.startsWithAscii(offset: Int, text: String): Boolean {
            if (offset < 0 || offset + text.length > size) return false
            for (i in text.indices) if (this[offset + i] != text[i].code.toByte()) return false
            return true
        }

        private fun ByteArray.u32(offset: Int): Long {
            if (offset < 0 || offset + 4 > size) return -1L
            return (this[offset].toLong() and 0xffL) or
                ((this[offset + 1].toLong() and 0xffL) shl 8) or
                ((this[offset + 2].toLong() and 0xffL) shl 16) or
                ((this[offset + 3].toLong() and 0xffL) shl 24)
        }

        private fun ByteArray.u64(offset: Int): Long {
            if (offset < 0 || offset + 8 > size) return -1L
            val low = u32(offset)
            val high = u32(offset + 4)
            if (high < 0L || high > 0x7fff_ffffL) return -1L
            return low or (high shl 32)
        }
    }
}

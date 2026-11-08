package com.tolete.player.data

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import java.io.IOException
import kotlin.math.min

/** MIME privado usado para encaminhar o fluxo DSD ao decoder FFmpeg habilitado para DSF. */
object DsfMimeTypes {
    const val AUDIO_DSD = "audio/x-dsd"
}

/** Extrai blocos DSD planos de um arquivo DSF para o decoder DSD do FFmpeg. */
@UnstableApi
class DsfExtractor : Extractor {
    private var output: ExtractorOutput? = null
    private var track: TrackOutput? = null
    private var header: DsfHeader? = null
    private var bytesReadPerChannel = 0L

    override fun sniff(input: ExtractorInput): Boolean {
        val peek = ByteArray(12)
        return try {
            if (!input.peekFully(peek, 0, peek.size, true)) return false
            peek[0] == 'D'.code.toByte() &&
                peek[1] == 'S'.code.toByte() &&
                peek[2] == 'D'.code.toByte() &&
                peek[3] == ' '.code.toByte() &&
                littleEndianLong(peek, 4) == 28L
        } catch (_: IOException) {
            false
        } finally {
            input.resetPeekPosition()
        }
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        track = output.track(0, C.TRACK_TYPE_AUDIO)
        output.endTracks()
    }

    @Throws(IOException::class)
    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val audioTrack = track ?: throw IOException("O extrator DSF ainda não foi inicializado")
        var fileHeader = header
        if (fileHeader == null) {
            val rawHeader = ByteArray(DsfHeader.HEADER_SIZE)
            input.readFully(rawHeader, 0, rawHeader.size)
            fileHeader = DsfHeader.parse(rawHeader)
                ?: throw IOException("Cabeçalho DSF inválido ou não suportado")
            header = fileHeader
            audioTrack.format(
                Format.Builder()
                    .setSampleMimeType(DsfMimeTypes.AUDIO_DSD)
                    .setSampleRate(fileHeader.pcmSampleRateHz)
                    .setChannelCount(fileHeader.channelCount)
                    .setAverageBitrate((fileHeader.dsdSampleRateHz.toLong() * fileHeader.channelCount)
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    .setMaxInputSize(fileHeader.blockSizePerChannel * fileHeader.channelCount)
                    .build()
            )
            output?.seekMap(DsfSeekMap(fileHeader))
        }

        val remaining = fileHeader.bytesPerChannel - bytesReadPerChannel
        if (remaining <= 0L) return Extractor.RESULT_END_OF_INPUT

        val bytesThisBlock = min(fileHeader.blockSizePerChannel.toLong(), remaining).toInt()
        val packet = ByteArray(bytesThisBlock * fileHeader.channelCount)
        val paddingPerChannel = fileHeader.blockSizePerChannel - bytesThisBlock
        for (channel in 0 until fileHeader.channelCount) {
            input.readFully(packet, channel * bytesThisBlock, bytesThisBlock)
            if (paddingPerChannel > 0) input.skipFully(paddingPerChannel)
        }

        val sampleTimeUs = bytesReadPerChannel * 8_000_000L / fileHeader.dsdSampleRateHz

        audioTrack.sampleData(ParsableByteArray(packet), packet.size)
        audioTrack.sampleMetadata(
            sampleTimeUs,
            C.BUFFER_FLAG_KEY_FRAME,
            packet.size,
            0,
            null
        )
        bytesReadPerChannel += bytesThisBlock
        return Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        if (position == 0L) header = null
        val fileHeader = header
        if (fileHeader == null || position <= fileHeader.dataStartOffset) {
            bytesReadPerChannel = 0L
            return
        }
        val bytesPerBlockGroup = fileHeader.blockSizePerChannel.toLong() * fileHeader.channelCount
        val blockIndex = (position - fileHeader.dataStartOffset) / bytesPerBlockGroup
        bytesReadPerChannel = (blockIndex * fileHeader.blockSizePerChannel)
            .coerceAtMost(fileHeader.bytesPerChannel)
    }

    override fun release() = Unit

    private class DsfSeekMap(private val header: DsfHeader) : SeekMap {
        override fun isSeekable(): Boolean = true
        override fun getDurationUs(): Long = header.durationUs

        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            val targetUs = timeUs.coerceIn(0L, header.durationUs)
            val targetBits = targetUs * header.dsdSampleRateHz / 1_000_000L
            val targetBytes = targetBits / 8L
            val blockIndex = targetBytes / header.blockSizePerChannel
            val bytesPerChannel = (blockIndex * header.blockSizePerChannel)
                .coerceAtMost(header.bytesPerChannel)
            val pointTimeUs = bytesPerChannel * 8_000_000L / header.dsdSampleRateHz
            val position = header.dataStartOffset +
                blockIndex * header.blockSizePerChannel * header.channelCount
            return SeekMap.SeekPoints(SeekPoint(pointTimeUs, position))
        }
    }

    private companion object {
        fun littleEndianLong(bytes: ByteArray, offset: Int): Long {
            var value = 0L
            for (index in 0 until 8) {
                value = value or ((bytes[offset + index].toLong() and 0xffL) shl (8 * index))
            }
            return value
        }
    }
}

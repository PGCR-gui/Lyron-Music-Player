package com.tolete.player.ui

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory as Media3ExtractorsFactory
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import com.tolete.player.data.DsfExtractor
import com.tolete.player.data.HighRatePcmDownsampler
import com.tolete.player.data.Track

/**
 * Player efêmero da tela de edição: não usa MediaSession/PlaybackService, fila global,
 * notificação, scrobbler, fade de pausa ou crossfade. A fonte fica limitada a um track.
 */
@OptIn(UnstableApi::class)
internal fun createEditorPreviewPlayer(context: Context, track: Track): ExoPlayer {
    val appContext = context.applicationContext
    val isDsf = track.path.endsWith(".dsf", ignoreCase = true) ||
        track.path.endsWith(".dff", ignoreCase = true) ||
        track.codec?.contains("DSD", ignoreCase = true) == true ||
        track.codec?.contains("DSF", ignoreCase = true) == true

    val renderersFactory = object : DefaultRenderersFactory(appContext) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParameters: Boolean
        ): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(true)
                .setAudioProcessors(
                    arrayOf(
                        HighRatePcmDownsampler(
                            dsfSourceProvider = { isDsf },
                            forceStereoProvider = { channels -> channels > 2 }
                        )
                    )
                )
                .setEnableAudioOutputPlaybackParameters(true)
                .build()
        }

        override fun buildAudioRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: MediaCodecSelector,
            enableDecoderFallback: Boolean,
            audioSink: AudioSink,
            eventHandler: android.os.Handler,
            eventListener: AudioRendererEventListener,
            out: java.util.ArrayList<Renderer>
        ) {
            if (FfmpegLibrary.isAvailable()) {
                out.add(FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
            }
            super.buildAudioRenderers(
                context,
                extensionRendererMode,
                mediaCodecSelector,
                enableDecoderFallback,
                audioSink,
                eventHandler,
                eventListener,
                out
            )
        }
    }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

    val mediaSourceFactory = DefaultMediaSourceFactory(
        appContext,
        Media3ExtractorsFactory { arrayOf(DsfExtractor(), *DefaultExtractorsFactory().createExtractors()) }
    )
    return ExoPlayer.Builder(appContext, renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus= */ false
        )
        .build()
        .also { player ->
            player.setMediaItem(MediaItem.fromUri(track.uri))
            player.prepare()
        }
}

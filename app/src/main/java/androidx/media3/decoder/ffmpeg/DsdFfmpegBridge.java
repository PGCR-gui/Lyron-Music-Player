package androidx.media3.decoder.ffmpeg;

/** Initializes FFmpeg's DSD codec with the channel layout required by its decoder. */
final class DsdFfmpegBridge {

  static {
    // libffmpegJNI is loaded by FfmpegLibrary before a DSD decoder is constructed.
    System.loadLibrary("ffmpegDsdBridge");
  }

  private DsdFfmpegBridge() {}

  static native long initialize(
      String codecName, boolean outputFloat, int sampleRate, int channelCount);
}

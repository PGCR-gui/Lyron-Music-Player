#include <android/log.h>
#include <jni.h>
#include <string.h>

#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>

#define LOG_TAG "ffmpeg_dsd_jni"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))

JNIEXPORT jlong JNICALL
Java_androidx_media3_decoder_ffmpeg_DsdFfmpegBridge_initialize(
    JNIEnv *env, jclass clazz, jstring codec_name, jboolean output_float,
    jint raw_sample_rate, jint raw_channel_count) {
  (void)clazz;
  if (codec_name == NULL || raw_sample_rate <= 0 || raw_channel_count <= 0) {
    LOGE("Invalid DSD decoder parameters: rate=%d channels=%d",
         raw_sample_rate, raw_channel_count);
    return 0;
  }

  const char *codec_name_chars = (*env)->GetStringUTFChars(env, codec_name, NULL);
  if (codec_name_chars == NULL) {
    LOGE("Could not read DSD codec name");
    return 0;
  }

  const AVCodec *codec = avcodec_find_decoder_by_name(codec_name_chars);
  if (codec == NULL) {
    LOGE("FFmpeg decoder not found: %s", codec_name_chars);
    (*env)->ReleaseStringUTFChars(env, codec_name, codec_name_chars);
    return 0;
  }

  AVCodecContext *context = avcodec_alloc_context3(codec);
  if (context == NULL) {
    LOGE("Failed to allocate DSD codec context");
    (*env)->ReleaseStringUTFChars(env, codec_name, codec_name_chars);
    return 0;
  }

  context->request_sample_fmt = output_float ? AV_SAMPLE_FMT_FLT : AV_SAMPLE_FMT_S16;
  context->sample_rate = raw_sample_rate;
  av_channel_layout_default(&context->ch_layout, raw_channel_count);
  context->err_recognition = AV_EF_IGNORE_ERR;

  const int result = avcodec_open2(context, codec, NULL);
  if (result < 0) {
    char error[AV_ERROR_MAX_STRING_SIZE];
    av_strerror(result, error, sizeof(error));
    LOGE("avcodec_open2 failed for %s (%d Hz, %d channels): %s",
         codec_name_chars, raw_sample_rate, raw_channel_count, error);
    avcodec_free_context(&context);
    (*env)->ReleaseStringUTFChars(env, codec_name, codec_name_chars);
    return 0;
  }

  LOGI("Initialized %s at %d Hz, %d channels, outputFloat=%d",
       codec_name_chars, raw_sample_rate, raw_channel_count, output_float);
  (*env)->ReleaseStringUTFChars(env, codec_name, codec_name_chars);
  return (jlong)context;
}

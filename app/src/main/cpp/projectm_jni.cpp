// Ponte JNI mínima entre o app e a libprojectM v4 (API C).
// Todas as funções são chamadas pela thread do GL (contexto EGL ativo).

#include <jni.h>
#include <android/log.h>
#include <cstddef>

#include <projectM-4/projectM.h>

#define LOG_TAG "ProjectM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define JNI_FN(name) Java_com_tolete_player_visualizer_ProjectMNative_##name

extern "C" {

JNIEXPORT jlong JNICALL JNI_FN(nativeCreate)(
        JNIEnv* env, jobject /*thiz*/,
        jint width, jint height, jstring texturePath,
        jint meshW, jint meshH, jint fps) {

    projectm_handle pm = projectm_create();   // exige contexto GL já ativo
    if (!pm) {
        LOGE("projectm_create falhou (contexto GL ativo? libprojectM compilada com ENABLE_GLES?)");
        return 0;
    }

    projectm_set_window_size(pm, (size_t) width, (size_t) height);
    projectm_set_mesh_size(pm, (size_t) meshW, (size_t) meshH);
    projectm_set_fps(pm, fps);

    if (texturePath != nullptr) {
        const char* path = env->GetStringUTFChars(texturePath, nullptr);
        const char* paths[] = { path };
        projectm_set_texture_search_paths(pm, paths, 1); // copia a string internamente
        env->ReleaseStringUTFChars(texturePath, path);
    }

    LOGI("projectM criado (%dx%d)", width, height);
    return reinterpret_cast<jlong>(pm);
}

JNIEXPORT void JNICALL JNI_FN(nativeResize)(
        JNIEnv*, jobject, jlong handle, jint width, jint height) {
    if (!handle) return;
    projectm_set_window_size(reinterpret_cast<projectm_handle>(handle),
                             (size_t) width, (size_t) height);
}

JNIEXPORT void JNICALL JNI_FN(nativeAddPcm)(
        JNIEnv* env, jobject, jlong handle, jfloatArray data, jint frames) {
    if (!handle || frames <= 0) return;
    jfloat* samples = env->GetFloatArrayElements(data, nullptr);
    // estéreo intercalado (L,R,L,R...); "count" = amostras POR CANAL
    projectm_pcm_add_float(reinterpret_cast<projectm_handle>(handle),
                           samples, (unsigned int) frames, PROJECTM_STEREO);
    env->ReleaseFloatArrayElements(data, samples, JNI_ABORT);
}

JNIEXPORT void JNICALL JNI_FN(nativeLoadPreset)(
        JNIEnv* env, jobject, jlong handle, jstring path, jboolean smooth) {
    if (!handle || !path) return;
    const char* file = env->GetStringUTFChars(path, nullptr);
    projectm_load_preset_file(reinterpret_cast<projectm_handle>(handle), file, smooth == JNI_TRUE);
    env->ReleaseStringUTFChars(path, file);
}

JNIEXPORT void JNICALL JNI_FN(nativeRender)(JNIEnv*, jobject, jlong handle) {
    if (!handle) return;
    projectm_opengl_render_frame(reinterpret_cast<projectm_handle>(handle));
}

JNIEXPORT void JNICALL JNI_FN(nativeDestroy)(JNIEnv*, jobject, jlong handle) {
    if (!handle) return;
    projectm_destroy(reinterpret_cast<projectm_handle>(handle));
    LOGI("projectM destruído");
}

} // extern "C"

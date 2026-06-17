#include <jni.h>
#include <android/log.h>

#define LOG_TAG "openconverter_ffmpeg"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_openconverter_ffmpeg_FfmpegBridge_transcode(
    JNIEnv *env,
    jobject /* this */,
    jbyteArray inputBytes,
    jstring inputFormat,
    jstring outputFormat,
    jint bitrateKbps
) {
    // M1 STUB: pass through. Real implementation in Task 3.5 will use
    // ffmpeg's avcodec API. For M1, the NcmDecoder produces raw MP3 bytes
    // which we copy through unchanged.
    const char *in_fmt = env->GetStringUTFChars(inputFormat, nullptr);
    const char *out_fmt = env->GetStringUTFChars(outputFormat, nullptr);
    ALOGI("transcode stub: %s -> %s @ %dkbps", in_fmt, out_fmt, bitrateKbps);
    env->ReleaseStringUTFChars(inputFormat, in_fmt);
    env->ReleaseStringUTFChars(outputFormat, out_fmt);

    jsize len = env->GetArrayLength(inputBytes);
    jbyte *in_data = env->GetByteArrayElements(inputBytes, nullptr);
    jbyteArray output = env->NewByteArray(len);
    env->SetByteArrayRegion(output, 0, len, in_data);
    env->ReleaseByteArrayElements(inputBytes, in_data, JNI_ABORT);
    return output;
}

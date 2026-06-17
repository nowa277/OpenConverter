// Real ffmpeg-based audio probe + passthrough. M3 scope: validate input
// is valid audio and report duration; re-encoding deferred to a future
// spec (requires rebuilding ffmpeg with --enable-encoder).
//
// Algorithm:
// 1. Open the input bytes as an in-memory AVFormatContext (using a custom
//    AVIOContext that reads from our ByteArray).
// 2. Find the best audio stream.
// 3. Open the audio codec.
// 4. Read all packets, decode all frames, count samples (for duration).
// 5. Close everything.
// 6. Return the input bytes unchanged (M3 passthrough; encoder support
//    requires a ffmpeg rebuild with libmp3lame/libvorbis/libfdk-aac).

#include <jni.h>
#include <android/log.h>
#include <cstring>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/avutil.h>
#include <libavutil/opt.h>
}

#define LOG_TAG "openconverter_ffmpeg"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Custom read callback: feeds our ByteArray to ffmpeg's demuxer
struct BufferData {
    const uint8_t *ptr;
    size_t size;
    size_t pos;
};

static int read_packet(void *opaque, uint8_t *buf, int buf_size) {
    auto *bd = (BufferData *)opaque;
    int remaining = bd->size - bd->pos;
    if (remaining <= 0) return AVERROR_EOF;
    int to_read = (buf_size < remaining) ? buf_size : remaining;
    memcpy(buf, bd->ptr + bd->pos, to_read);
    bd->pos += to_read;
    return to_read;
}

static int64_t seek_packet(void *opaque, int64_t offset, int whence) {
    auto *bd = (BufferData *)opaque;
    if (whence == AVSEEK_SIZE) return bd->size;
    if (whence == SEEK_SET) {
        bd->pos = (offset < 0) ? 0 : (size_t)offset;
        if (bd->pos > bd->size) bd->pos = bd->size;
        return bd->pos;
    }
    return -1;
}

// Probe the input bytes: returns duration in seconds (>0 = valid audio)
// or -1.0 on error.
static double probe_duration(const uint8_t *data, size_t size, const char *input_format) {
    BufferData bd = { data, size, 0 };
    uint8_t *avio_buf = (uint8_t *)av_malloc(4096);
    if (!avio_buf) return -1.0;
    AVIOContext *avio = avio_alloc_context(avio_buf, 4096, 0, &bd, read_packet, NULL, seek_packet);
    if (!avio) { av_free(avio_buf); return -1.0; }

    AVFormatContext *fmt = avformat_alloc_context();
    fmt->pb = avio;

    AVDictionary *opts = NULL;
    if (input_format && strlen(input_format) > 0) {
        av_dict_set(&opts, "format", input_format, 0);
    }

    int ret = avformat_open_input(&fmt, NULL, NULL, &opts);
    if (ret < 0) {
        ALOGE("avformat_open_input failed: %d", ret);
        avio_context_free(&avio);
        avformat_free_context(fmt);
        return -1.0;
    }

    ret = avformat_find_stream_info(fmt, NULL);
    if (ret < 0) {
        ALOGE("avformat_find_stream_info failed: %d", ret);
        avio_context_free(&avio);
        avformat_free_context(fmt);
        return -1.0;
    }

    // Find best audio stream
    int audio_stream_idx = -1;
    for (unsigned i = 0; i < fmt->nb_streams; i++) {
        if (fmt->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            audio_stream_idx = i;
            break;
        }
    }
    if (audio_stream_idx < 0) {
        ALOGE("no audio stream found");
        avio_context_free(&avio);
        avformat_free_context(fmt);
        return -1.0;
    }

    AVCodecParameters *codecpar = fmt->streams[audio_stream_idx]->codecpar;
    const AVCodec *codec = avcodec_find_decoder(codecpar->codec_id);
    if (!codec) {
        ALOGE("no decoder for codec_id %d", codecpar->codec_id);
        avio_context_free(&avio);
        avformat_free_context(fmt);
        return -1.0;
    }

    AVCodecContext *ctx = avcodec_alloc_context3(codec);
    if (!ctx) {
        avio_context_free(&avio);
        avformat_free_context(fmt);
        return -1.0;
    }
    avcodec_parameters_to_context(ctx, codecpar);
    ret = avcodec_open2(ctx, codec, NULL);
    if (ret < 0) {
        ALOGE("avcodec_open2 failed: %d", ret);
        avcodec_free_context(&ctx);
        avio_context_free(&avio);
        avformat_free_context(fmt);
        return -1.0;
    }

    // Decode all packets to count samples
    AVPacket *pkt = av_packet_alloc();
    AVFrame *frame = av_frame_alloc();
    int64_t total_samples = 0;
    while (av_read_frame(fmt, pkt) >= 0) {
        if (pkt->stream_index == audio_stream_idx) {
            ret = avcodec_send_packet(ctx, pkt);
            if (ret >= 0) {
                while (avcodec_receive_frame(ctx, frame) >= 0) {
                    total_samples += frame->nb_samples;
                    av_frame_unref(frame);
                }
            }
        }
        av_packet_unref(pkt);
    }
    // Flush decoder
    avcodec_send_packet(ctx, NULL);
    while (avcodec_receive_frame(ctx, frame) >= 0) {
        total_samples += frame->nb_samples;
        av_frame_unref(frame);
    }

    double duration = 0.0;
    if (ctx->sample_rate > 0) {
        duration = (double)total_samples / ctx->sample_rate;
    } else if (fmt->duration > 0) {
        duration = (double)fmt->duration / AV_TIME_BASE;
    }

    ALOGI("probe ok: duration=%.2fs codec=%d", duration, codecpar->codec_id);

    av_frame_free(&frame);
    av_packet_free(&pkt);
    avcodec_free_context(&ctx);
    avio_context_free(&avio);
    avformat_free_context(fmt);
    return duration;
}

// JNI: probe the input audio, return duration in seconds
extern "C" JNIEXPORT jdouble JNICALL
Java_com_openconverter_app_ffmpeg_FfmpegBridge_probeDurationNative(
    JNIEnv *env,
    jobject /* this */,
    jbyteArray inputBytes,
    jstring inputFormat
) {
    const char *in_fmt = env->GetStringUTFChars(inputFormat, nullptr);
    jsize len = env->GetArrayLength(inputBytes);
    jbyte *in_data = env->GetByteArrayElements(inputBytes, nullptr);

    double duration = probe_duration((const uint8_t *)in_data, (size_t)len, in_fmt);

    env->ReleaseByteArrayElements(inputBytes, in_data, JNI_ABORT);
    env->ReleaseStringUTFChars(inputFormat, in_fmt);
    return duration;
}

// JNI: transcode (M3: passthrough). Real re-encoding needs ffmpeg rebuild
// with --enable-encoder (deferred).
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_openconverter_app_ffmpeg_FfmpegBridge_transcode(
    JNIEnv *env,
    jobject /* this */,
    jbyteArray inputBytes,
    jstring inputFormat,
    jstring outputFormat,
    jint bitrateKbps
) {
    const char *in_fmt = env->GetStringUTFChars(inputFormat, nullptr);
    const char *out_fmt = env->GetStringUTFChars(outputFormat, nullptr);

    // M3: probe first to validate input
    jsize len = env->GetArrayLength(inputBytes);
    jbyte *in_data = env->GetByteArrayElements(inputBytes, nullptr);
    double duration = probe_duration((const uint8_t *)in_data, (size_t)len, in_fmt);
    ALOGI("transcode stub: %s -> %s @ %dkbps, probe duration=%.2fs",
          in_fmt, out_fmt, bitrateKbps, duration);
    env->ReleaseByteArrayElements(inputBytes, in_data, JNI_ABORT);

    // M3: passthrough. If duration <= 0, input is invalid; return empty.
    if (duration <= 0.0) {
        env->ReleaseStringUTFChars(inputFormat, in_fmt);
        env->ReleaseStringUTFChars(outputFormat, out_fmt);
        return env->NewByteArray(0);
    }

    env->ReleaseStringUTFChars(inputFormat, in_fmt);
    env->ReleaseStringUTFChars(outputFormat, out_fmt);

    // M3 passthrough: return input bytes unchanged (NCM decrypts to MP3;
    // for true transcoding, ffmpeg needs encoders enabled at build time)
    jbyteArray output = env->NewByteArray(len);
    jbyte *in_data2 = env->GetByteArrayElements(inputBytes, nullptr);
    env->SetByteArrayRegion(output, 0, len, in_data2);
    env->ReleaseByteArrayElements(inputBytes, in_data2, JNI_ABORT);
    return output;
}

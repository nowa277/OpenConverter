// ffmpeg_transcode.c — real audio transcoding (v0.3.0).
//
// Pipeline: avformat_open_input → find decoder → avcodec_send_packet /
// receive_frame (PCM) → find encoder → avcodec_send_frame /
// receive_packet → av_write_frame → avio_close_dyn_buf → ByteArray.
//
// This file is paired with ffmpeg_jni.cpp. The JNI bridge lives there
// (Java_com_openconverter_app_ffmpeg_FfmpegBridge_transcode). The actual
// transcoding logic is here for testability — it's plain C, no JNI
// imports, can be unit-tested with a host build later.

#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavcodec/codec_id.h>
#include <libavutil/avutil.h>
#include <libavutil/opt.h>
#include <libavutil/channel_layout.h>
#include <libavutil/samplefmt.h>
#include <libavutil/audio_fifo.h>
#include <libswresample/swresample.h>

#include <stdio.h>
#include <string.h>

// Read callback for in-memory input
typedef struct {
    const uint8_t *ptr;
    size_t size;
    size_t pos;
} BufferData;

static int read_packet(void *opaque, uint8_t *buf, int buf_size) {
    BufferData *bd = (BufferData *)opaque;
    int remaining = bd->size - bd->pos;
    if (remaining <= 0) return AVERROR_EOF;
    int to_read = (buf_size < remaining) ? buf_size : remaining;
    memcpy(buf, bd->ptr + bd->pos, to_read);
    bd->pos += to_read;
    return to_read;
}

static int64_t seek_packet(void *opaque, int64_t offset, int whence) {
    BufferData *bd = (BufferData *)opaque;
    if (whence == AVSEEK_SIZE) return bd->size;
    if (whence == SEEK_SET) {
        bd->pos = (offset < 0) ? 0 : (size_t)offset;
        if (bd->pos > bd->size) bd->pos = bd->size;
        return bd->pos;
    }
    return -1;
}

// Map output format string to AVCodecID + muxer name
typedef struct {
    const char *name;
    enum AVCodecID codec_id;
    const char *muxer_short_name;
} TargetCodec;

static const TargetCodec TARGETS[] = {
    { "mp3",  AV_CODEC_ID_MP3,        "mp3"  },
    { "flac", AV_CODEC_ID_FLAC,       "flac" },
    { "wav",  AV_CODEC_ID_PCM_S16LE,  "wav"  },
    { "m4a",  AV_CODEC_ID_AAC,        "ipod" },  // m4a uses mp4 muxer, "ipod" tag
    { "ogg",  AV_CODEC_ID_VORBIS,     "ogg"  },
};
static const int TARGETS_LEN = sizeof(TARGETS) / sizeof(TARGETS[0]);

static const TargetCodec *find_target(const char *name) {
    for (int i = 0; i < TARGETS_LEN; i++) {
        if (strcmp(TARGETS[i].name, name) == 0) return &TARGETS[i];
    }
    return NULL;
}

// transcode_main: in-memory audio transcode.
// On success, allocates *out_data (caller frees) and writes *out_size.
// On failure, returns negative AVERROR code.
int transcode_main(
    const uint8_t *in_data, size_t in_size,
    const char *in_format,    // e.g. "mp3" (decrypted container)
    const char *out_format,   // e.g. "mp3" / "flac" / "wav" / "m4a" / "ogg"
    int bitrate_kbps,
    uint8_t **out_data, size_t *out_size
) {
    const TargetCodec *target = find_target(out_format);
    if (!target) return -1;

    // === Input: open AVFormatContext with custom AVIOContext ===
    BufferData bd = { in_data, in_size, 0 };
    uint8_t *avio_buf = (uint8_t *)av_malloc(4096);
    if (!avio_buf) return -2;
    AVIOContext *avio = avio_alloc_context(avio_buf, 4096, 0, &bd, read_packet, NULL, seek_packet);
    if (!avio) { av_free(avio_buf); return -3; }

    AVFormatContext *fmt_in = avformat_alloc_context();
    fmt_in->pb = avio;
    AVDictionary *opts_in = NULL;
    if (in_format && strlen(in_format) > 0) {
        av_dict_set(&opts_in, "format", in_format, 0);
    }
    int ret = avformat_open_input(&fmt_in, NULL, NULL, &opts_in);
    if (ret < 0) goto fail_input;

    ret = avformat_find_stream_info(fmt_in, NULL);
    if (ret < 0) goto fail_input;

    int audio_idx = -1;
    for (unsigned i = 0; i < fmt_in->nb_streams; i++) {
        if (fmt_in->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            audio_idx = i;
            break;
        }
    }
    if (audio_idx < 0) goto fail_input;

    AVCodecParameters *dec_par = fmt_in->streams[audio_idx]->codecpar;
    const AVCodec *dec = avcodec_find_decoder(dec_par->codec_id);
    if (!dec) goto fail_input;

    AVCodecContext *dec_ctx = avcodec_alloc_context3(dec);
    if (!dec_ctx) goto fail_input;
    avcodec_parameters_to_context(dec_ctx, dec_par);
    ret = avcodec_open2(dec_ctx, dec, NULL);
    if (ret < 0) goto fail_decoder;

    // === Output: find encoder + allocate context ===
    const AVCodec *enc = avcodec_find_encoder(target->codec_id);
    if (!enc) goto fail_decoder;

    AVCodecContext *enc_ctx = avcodec_alloc_context3(enc);
    if (!enc_ctx) goto fail_decoder;

    enc_ctx->bit_rate = (bitrate_kbps > 0) ? bitrate_kbps * 1000 : 128000;
    enc_ctx->sample_rate = dec_ctx->sample_rate;
    enc_ctx->sample_fmt = (enc->sample_fmts ? enc->sample_fmts[0] : AV_SAMPLE_FMT_FLTP);
    enc_ctx->channel_layout = (dec_ctx->channel_layout > 0) ?
        dec_ctx->channel_layout : AV_CH_LAYOUT_STEREO;
    enc_ctx->channels = av_get_channel_layout_nb_channels(enc_ctx->channel_layout);
    enc_ctx->time_base = (AVRational){ 1, enc_ctx->sample_rate };

    if (target->codec_id == AV_CODEC_ID_MP3) {
        enc_ctx->bit_rate = 192000;
    }

    if (avcodec_open2(enc_ctx, enc, NULL) < 0) goto fail_encoder;

    // === Output muxer: write to dynamic buffer ===
    AVFormatContext *fmt_out = NULL;
    if (avformat_alloc_output_context2(&fmt_out, NULL,
            target->muxer_short_name, NULL) < 0) goto fail_encoder;
    AVStream *out_stream = avformat_new_stream(fmt_out, NULL);
    if (!out_stream) goto fail_output;
    avcodec_parameters_from_context(out_stream->codecpar, enc_ctx);
    out_stream->time_base = enc_ctx->time_base;

    uint8_t *dyn_buf = NULL;
    int dyn_buf_size = 0;
    fmt_out->pb = avio_alloc_context(
        (uint8_t *)av_malloc(4096), 4096, 1, NULL, NULL, NULL, NULL);
    if (!fmt_out->pb) goto fail_output;
    avio_open_dyn_buf(&fmt_out->pb);  // overrides pb with dyn buf

    if (avformat_write_header(fmt_out, NULL) < 0) goto fail_output;

    // === Resampler: convert decoder's sample format to encoder's ===
    SwrContext *swr = swr_alloc();
    av_opt_set_int(swr, "in_channel_layout", dec_ctx->channel_layout, 0);
    av_opt_set_int(swr, "in_sample_rate", dec_ctx->sample_rate, 0);
    av_opt_set_sample_fmt(swr, "in_sample_fmt", dec_ctx->sample_fmt, 0);
    av_opt_set_int(swr, "out_channel_layout", enc_ctx->channel_layout, 0);
    av_opt_set_int(swr, "out_sample_rate", enc_ctx->sample_rate, 0);
    av_opt_set_sample_fmt(swr, "out_sample_fmt", enc_ctx->sample_fmt, 0);
    if (swr_init(swr) < 0) { swr_free(&swr); goto fail_output; }

    // === Main loop: decode → resample → encode → write ===
    AVPacket *pkt = av_packet_alloc();
    AVFrame *dec_frame = av_frame_alloc();
    AVFrame *enc_frame = av_frame_alloc();
    int64_t pts = 0;
    int encode_ret = 0;

    while (av_read_frame(fmt_in, pkt) >= 0) {
        if (pkt->stream_index == audio_idx) {
            ret = avcodec_send_packet(dec_ctx, pkt);
            if (ret >= 0) {
                while (avcodec_receive_frame(dec_ctx, dec_frame) >= 0) {
                    // Resample
                    enc_frame->sample_rate = enc_ctx->sample_rate;
                    enc_frame->channel_layout = enc_ctx->channel_layout;
                    enc_frame->channels = enc_ctx->channels;
                    enc_frame->sample_fmt = enc_ctx->sample_fmt;
                    enc_frame->nb_samples = dec_frame->nb_samples;
                    enc_frame->pts = pts;
                    pts += dec_frame->nb_samples;

                    av_frame_get_buffer(enc_frame, 0);
                    swr_convert(swr,
                        enc_frame->data, enc_frame->nb_samples,
                        (const uint8_t **)dec_frame->data, dec_frame->nb_samples);

                    ret = avcodec_send_frame(enc_ctx, enc_frame);
                    if (ret < 0) break;
                    while ((encode_ret = avcodec_receive_packet(enc_ctx, pkt)) >= 0) {
                        av_packet_rescale_ts(pkt, enc_ctx->time_base, out_stream->time_base);
                        pkt->stream_index = out_stream->index;
                        av_interleaved_write_frame(fmt_out, pkt);
                        av_packet_unref(pkt);
                    }
                    av_frame_unref(enc_frame);
                }
            }
        }
        av_packet_unref(pkt);
    }

    // Flush decoder
    avcodec_send_packet(dec_ctx, NULL);
    while (avcodec_receive_frame(dec_ctx, dec_frame) >= 0) {
        enc_frame->sample_rate = enc_ctx->sample_rate;
        enc_frame->channel_layout = enc_ctx->channel_layout;
        enc_frame->channels = enc_ctx->channels;
        enc_frame->sample_fmt = enc_ctx->sample_fmt;
        enc_frame->nb_samples = dec_frame->nb_samples;
        enc_frame->pts = pts;
        pts += dec_frame->nb_samples;

        av_frame_get_buffer(enc_frame, 0);
        swr_convert(swr, enc_frame->data, enc_frame->nb_samples,
            (const uint8_t **)dec_frame->data, dec_frame->nb_samples);

        avcodec_send_frame(enc_ctx, enc_frame);
        while (avcodec_receive_packet(enc_ctx, pkt) >= 0) {
            av_packet_rescale_ts(pkt, enc_ctx->time_base, out_stream->time_base);
            pkt->stream_index = out_stream->index;
            av_interleaved_write_frame(fmt_out, pkt);
            av_packet_unref(pkt);
        }
        av_frame_unref(enc_frame);
    }

    // Flush encoder
    avcodec_send_frame(enc_ctx, NULL);
    while (avcodec_receive_packet(enc_ctx, pkt) >= 0) {
        av_packet_rescale_ts(pkt, enc_ctx->time_base, out_stream->time_base);
        pkt->stream_index = out_stream->index;
        av_interleaved_write_frame(fmt_out, pkt);
        av_packet_unref(pkt);
    }

    av_write_trailer(fmt_out);

    // Get dyn buffer
    dyn_buf_size = avio_close_dyn_buf(fmt_out->pb, &dyn_buf);
    fmt_out->pb = NULL;

    *out_data = dyn_buf;
    *out_size = (size_t)dyn_buf_size;

    // Cleanup
    av_frame_free(&enc_frame);
    av_frame_free(&dec_frame);
    av_packet_free(&pkt);
    swr_free(&swr);
    avcodec_free_context(&enc_ctx);
    avcodec_free_context(&dec_ctx);
    avformat_free_context(fmt_out);
    avformat_close_input(&fmt_in);
    return 0;

// error paths
fail_output:
    if (fmt_out) {
        if (fmt_out->pb) {
            uint8_t *tmp;
            avio_close_dyn_buf(fmt_out->pb, &tmp);
            av_free(tmp);
        }
        avformat_free_context(fmt_out);
    }
fail_encoder:
    if (enc_ctx) avcodec_free_context(&enc_ctx);
fail_decoder:
    if (dec_ctx) avcodec_free_context(&dec_ctx);
fail_input:
    if (fmt_in) avformat_close_input(&fmt_in);
    if (avio) {
        av_free(avio->buffer);
        avio_context_free(&avio);
    }
    return -100;
}

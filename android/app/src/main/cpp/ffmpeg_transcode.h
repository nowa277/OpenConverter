// ffmpeg_transcode.h — declaration of transcode_main() for JNI bridge.
#ifndef FFMPEG_TRANSCODE_H
#define FFMPEG_TRANSCODE_H

#include <stdint.h>
#include <stddef.h>

int transcode_main(
    const uint8_t *in_data, size_t in_size,
    const char *in_format,
    const char *out_format,
    int bitrate_kbps,
    uint8_t **out_data, size_t *out_size
);

#endif

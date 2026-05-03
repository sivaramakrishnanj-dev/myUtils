package com.srk.myutils.yd.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A single adaptive-format entry from {@code streamingData.adaptiveFormats[]}.
 *
 * <p>Video-only fields ({@code width}, {@code height}, {@code fps}) are empty for
 * audio formats. Audio-only field ({@code audioSampleRate}) is empty for video
 * formats. {@code contentLength} is optional because some formats omit it.
 *
 * @param itag            YouTube format identifier
 * @param mimeType        e.g. {@code "video/mp4; codecs=\"avc1.640028\""}
 * @param bitrate         total bitrate in bits/sec
 * @param width           pixel width (video only)
 * @param height          pixel height (video only)
 * @param fps             frames per second (video only)
 * @param audioSampleRate sample rate in Hz (audio only; InnerTube sends as string, parsed to int)
 * @param contentLength   byte length of the stream (optional)
 * @param url             direct CDN URL; empty string iff {@code signatureCipher} is non-empty
 * @param signatureCipher cipher payload; empty string when the URL is direct
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Format(
        int itag,
        String mimeType,
        long bitrate,
        OptionalInt width,
        OptionalInt height,
        OptionalInt fps,
        OptionalInt audioSampleRate,
        Optional<Long> contentLength,
        String url,
        String signatureCipher
) {

    /** {@code true} when this format carries a video stream. */
    public boolean isVideo() {
        return mimeType.startsWith("video/");
    }

    /** {@code true} when this format carries an audio stream. */
    public boolean isAudio() {
        return mimeType.startsWith("audio/");
    }

    /** {@code true} when the CDN URL requires JavaScript signature deciphering (AC-5.3). */
    public boolean hasCipher() {
        return !signatureCipher.isEmpty();
    }
}

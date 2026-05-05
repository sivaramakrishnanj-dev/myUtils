package com.srk.myutils.yd.core;

/**
 * Audio output format for {@code --audio-only} downloads (AC-2.3, AC-2.4).
 *
 * <p>{@link #M4A} is the default — direct stream copy, no re-encoding.
 * {@link #MP3} triggers an ffmpeg transcode at {@code NFR-DEFAULT-MP3-BITRATE = 192 kbps}.
 *
 * @see <a href="design/03-data-model.md">03-data-model.md § 2.5</a>
 */
public enum AudioFormat {

    /** Direct stream copy — no re-encoding required (AC-2.3). */
    M4A,

    /** Requires ffmpeg transcode via libmp3lame (AC-2.4). */
    MP3
}

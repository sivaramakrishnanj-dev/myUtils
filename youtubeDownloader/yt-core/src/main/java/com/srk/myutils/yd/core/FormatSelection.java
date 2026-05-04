package com.srk.myutils.yd.core;

/**
 * The result of format selection: a chosen video format and a chosen audio format.
 *
 * <p>Either field may be absent depending on the download mode — audio-only
 * operations leave {@code video} null; video+audio operations populate both.
 *
 * @param video selected video format (null for audio-only)
 * @param audio selected audio format (always non-null when returned)
 * @see FormatSelector
 */
public record FormatSelection(Format video, Format audio) { }

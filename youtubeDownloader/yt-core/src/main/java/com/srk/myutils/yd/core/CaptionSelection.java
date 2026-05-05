package com.srk.myutils.yd.core;

/**
 * The result of caption-track selection: the chosen track and whether ASR
 * fallback was used (AC-7.3, INV-16).
 *
 * @param track           the selected caption track
 * @param usedAsrFallback {@code true} when ASR was chosen because no manual track matched
 */
public record CaptionSelection(CaptionTrack track, boolean usedAsrFallback) { }

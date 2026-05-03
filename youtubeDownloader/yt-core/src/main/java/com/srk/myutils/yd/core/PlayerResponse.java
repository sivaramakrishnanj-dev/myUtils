package com.srk.myutils.yd.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Read-only view of the parsed InnerTube {@code /player} response.
 *
 * <p>Only the fields the tool consumes are modelled — everything else is
 * silently ignored per ADR-0004 ({@code @JsonIgnoreProperties(ignoreUnknown = true)}).
 *
 * @param videoDetails      video metadata (always present on a successful parse)
 * @param playabilityStatus playability enum value
 * @param adaptiveFormats   from {@code streamingData.adaptiveFormats}
 * @param captionTracks     from {@code captions.playerCaptionsTracklistRenderer.captionTracks}
 * @param thumbnails        from {@code videoDetails.thumbnail.thumbnails}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerResponse(
        VideoDetails videoDetails,
        PlayabilityStatus playabilityStatus,
        List<Format> adaptiveFormats,
        List<CaptionTrack> captionTracks,
        List<ThumbnailUrl> thumbnails
) { }

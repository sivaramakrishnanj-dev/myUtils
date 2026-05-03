package com.srk.myutils.yd.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Optional;

/**
 * Video metadata extracted from the InnerTube {@code videoDetails} object.
 *
 * @param videoId       validated 11-character YouTube video id
 * @param title         human-readable video title
 * @param isLive        {@code true} when the video is a live stream (AC-1.7)
 * @param isPrivate     {@code true} when the video is private
 * @param audioLanguage BCP-47 language of the primary audio track; often absent (AC-8.1 step 3)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VideoDetails(
        VideoId videoId,
        String title,
        boolean isLive,
        boolean isPrivate,
        Optional<LanguageCode> audioLanguage
) { }

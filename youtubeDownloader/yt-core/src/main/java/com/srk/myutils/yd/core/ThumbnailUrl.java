package com.srk.myutils.yd.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single thumbnail descriptor from {@code videoDetails.thumbnail.thumbnails[]}.
 *
 * @param url    HTTPS URL of the thumbnail image
 * @param width  pixel width
 * @param height pixel height
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThumbnailUrl(String url, int width, int height) { }

package com.srk.myutils.yd.core;

/**
 * Raw response from an InnerTube {@code /player} request.
 *
 * <p>Carries the HTTP status and the UTF-8 response body as a string.
 * Parsing into a {@link PlayerResponse} domain object is T-1.7's job
 * ({@code PlayerResponseExtractor}).
 *
 * @param httpStatus HTTP status code (e.g. 200)
 * @param body       response body as a UTF-8 string
 */
public record InnerTubeResponse(int httpStatus, String body) { }

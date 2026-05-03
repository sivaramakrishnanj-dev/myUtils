package com.srk.myutils.yd.core;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Parses a raw URL string into a validated {@link VideoId}.
 *
 * <p>Accepts the four URL shapes enumerated in AC-1.1:
 * <ul>
 *   <li>{@code https://www.youtube.com/watch?v=<id>}</li>
 *   <li>{@code https://youtu.be/<id>}</li>
 *   <li>{@code https://www.youtube.com/shorts/<id>}</li>
 *   <li>{@code https://m.youtube.com/watch?v=<id>}</li>
 * </ul>
 *
 * <p>Everything else throws {@link UrlParseException} (exit code 2, AC-5.2).
 * Pure function — no network or filesystem I/O (AC-11.1).
 */
public final class UrlParser {

    private static final String UNSUPPORTED_MSG =
            "Unsupported URL: %s — expected https://www.youtube.com/watch?v=..., "
                    + "https://youtu.be/..., https://www.youtube.com/shorts/..., "
                    + "or https://m.youtube.com/watch?v=...";

    /**
     * Parses a raw URL string and returns a validated {@link VideoId}.
     *
     * @param raw the URL string provided by the user
     * @return a validated {@code VideoId}
     * @throws UrlParseException if the URL is null, empty, malformed, or not one of the four accepted shapes
     */
    public VideoId parse(String raw) {
        if (raw == null) {
            throw new UrlParseException("Invalid URL: null");
        }
        if (raw.isBlank()) {
            throw new UrlParseException("Invalid URL: empty");
        }

        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw new UrlParseException("Invalid URL: " + raw);
        }

        if (!"https".equals(uri.getScheme())) {
            throw new UrlParseException(String.format(UNSUPPORTED_MSG, raw));
        }

        String host = uri.getHost();
        String path = uri.getPath();
        String candidate = switch (host) {
            case "www.youtube.com" -> extractFromWww(raw, path, uri.getQuery());
            case "m.youtube.com" -> extractFromMobile(raw, path, uri.getQuery());
            case "youtu.be" -> extractFromShortLink(raw, path);
            default -> throw new UrlParseException(String.format(UNSUPPORTED_MSG, raw));
        };

        return VideoId.of(candidate);
    }

    private static String extractFromWww(String raw, String path, String query) {
        if ("/watch".equals(path)) {
            return extractVParam(raw, query);
        }
        if (path != null && path.startsWith("/shorts/")) {
            String candidate = path.substring("/shorts/".length());
            if (candidate.isEmpty() || candidate.contains("/")) {
                throw new UrlParseException(String.format(UNSUPPORTED_MSG, raw));
            }
            return candidate;
        }
        throw new UrlParseException(String.format(UNSUPPORTED_MSG, raw));
    }

    private static String extractFromMobile(String raw, String path, String query) {
        if (!"/watch".equals(path)) {
            throw new UrlParseException(String.format(UNSUPPORTED_MSG, raw));
        }
        return extractVParam(raw, query);
    }

    private static String extractFromShortLink(String raw, String path) {
        if (path == null || path.length() <= 1) {
            throw new UrlParseException(String.format(UNSUPPORTED_MSG, raw));
        }
        String candidate = path.substring(1);
        if (candidate.contains("/")) {
            throw new UrlParseException(String.format(UNSUPPORTED_MSG, raw));
        }
        return candidate;
    }

    private static String extractVParam(String raw, String query) {
        if (query == null) {
            throw new UrlParseException("Missing v parameter: " + raw);
        }
        for (String param : query.split("&")) {
            if (param.startsWith("v=")) {
                String value = param.substring(2);
                if (value.isEmpty()) {
                    throw new UrlParseException("Missing v parameter: " + raw);
                }
                return value;
            }
        }
        throw new UrlParseException("Missing v parameter: " + raw);
    }
}

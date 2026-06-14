package com.srk.myutils.yd.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Parses a raw InnerTube {@code /player} JSON response into a
 * {@link PlayerResponse} domain object.
 *
 * <p>This is the wire-to-domain boundary (AC-11.1 pure function over
 * in-memory input). Uses Jackson's tree model ({@code readTree}) to walk
 * the deeply nested JSON and map it to flat domain records, keeping the
 * records free of Jackson annotations for nested paths.
 *
 * <p>Unknown fields are silently ignored per ADR-0004. Missing required
 * fields ({@code videoDetails}, {@code playabilityStatus}) throw
 * {@link InnerTubeParseException}.
 *
 * @see <a href="../../../../../design/adr/0004-jackson-for-json.md">ADR-0004</a>
 */
public final class PlayerResponseExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerResponseExtractor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    private PlayerResponseExtractor() { }

    /**
     * Post-parse playability check — validates that the video is downloadable.
     *
     * <p>Must be called <em>after</em> {@link #extract(String)}. Throws the
     * appropriate exception per AC-5.2 category mapping:
     * <ul>
     *   <li>Exit 21 ({@link LiveStreamException}): {@code isLive == true} OR
     *       {@code LIVE_STREAM_OFFLINE} (AC-1.7)</li>
     *   <li>Exit 20 ({@link VideoUnavailableException}): {@code UNPLAYABLE},
     *       {@code LOGIN_REQUIRED}, {@code AGE_VERIFICATION_REQUIRED},
     *       {@code ERROR}</li>
     *   <li>Exit 11 ({@link InnerTubeParseException}): {@code UNKNOWN}
     *       (unrecognized status — response shape may have changed)</li>
     * </ul>
     *
     * @param response parsed player response
     * @return the same response, for fluent chaining
     * @throws LiveStreamException       if the video is live or not-yet-premiered
     * @throws VideoUnavailableException if the video is private, deleted, or geo-blocked
     * @throws InnerTubeParseException   if the playability status is unrecognized
     */
    public static PlayerResponse checkPlayability(PlayerResponse response) {
        PlayabilityStatus status = response.playabilityStatus();
        VideoDetails details = response.videoDetails();

        // AC-1.7: live stream check (exit 21)
        if (details.isLive() || status == PlayabilityStatus.LIVE_STREAM_OFFLINE) {
            throw new LiveStreamException(
                    "Live streams are not supported in this MVP: " + details.videoId().value());
        }

        // AC-5.2 category 20: video unavailable
        if (status == PlayabilityStatus.UNPLAYABLE
                || status == PlayabilityStatus.LOGIN_REQUIRED
                || status == PlayabilityStatus.AGE_VERIFICATION_REQUIRED
                || status == PlayabilityStatus.ERROR) {
            throw new VideoUnavailableException(
                    "Video unavailable (" + status + "): " + details.videoId().value());
        }

        // UNKNOWN → exit 11 (parse error, not unavailable)
        if (status == PlayabilityStatus.UNKNOWN) {
            throw new InnerTubeParseException(
                    "Unknown playabilityStatus — response shape may have changed");
        }

        // status == OK — pass through
        return response;
    }

    /**
     * Parses the given JSON string into a {@link PlayerResponse}.
     *
     * @param json raw InnerTube response body (UTF-8 string)
     * @return parsed domain object
     * @throws InnerTubeParseException on malformed JSON, missing required
     *         fields, or invalid field values
     */
    public static PlayerResponse extract(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new InnerTubeParseException("Malformed InnerTube JSON", e);
        }

        JsonNode videoDetailsNode = requireNode(root, "videoDetails");
        JsonNode playabilityNode = requireNode(root, "playabilityStatus");

        VideoDetails videoDetails = parseVideoDetails(videoDetailsNode);
        PlayabilityStatus playabilityStatus = parsePlayabilityStatus(playabilityNode);
        List<Format> adaptiveFormats = parseAdaptiveFormats(root);
        List<CaptionTrack> captionTracks = parseCaptionTracks(root);
        List<ThumbnailUrl> thumbnails = parseThumbnails(videoDetailsNode);

        LOGGER.debug("Parsed PlayerResponse: videoId={}, status={}, formats={}, captions={}, thumbnails={}",
                videoDetails.videoId().value(), playabilityStatus,
                adaptiveFormats.size(), captionTracks.size(), thumbnails.size());

        return new PlayerResponse(videoDetails, playabilityStatus,
                adaptiveFormats, captionTracks, thumbnails);
    }

    private static VideoDetails parseVideoDetails(JsonNode node) {
        String rawVideoId = requireString(node, "videoId");
        VideoId videoId;
        try {
            videoId = VideoId.of(rawVideoId);
        } catch (UrlParseException e) {
            throw new InnerTubeParseException(
                    "videoDetails.videoId is invalid: " + rawVideoId, e);
        }

        String title = requireString(node, "title");
        boolean isLive = node.path("isLive").asBoolean(false);
        boolean isPrivate = node.path("isPrivate").asBoolean(false);

        Optional<LanguageCode> audioLanguage = Optional.empty();
        JsonNode langNode = node.get("audioLanguage");
        if (langNode != null && langNode.isTextual() && !langNode.asText().isEmpty()) {
            try {
                audioLanguage = Optional.of(LanguageCode.of(langNode.asText()));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Ignoring unparseable audioLanguage: {}", langNode.asText());
            }
        }

        return new VideoDetails(videoId, title, isLive, isPrivate, audioLanguage);
    }

    private static PlayabilityStatus parsePlayabilityStatus(JsonNode node) {
        String status = requireString(node, "status");
        try {
            return PlayabilityStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Unknown playabilityStatus '{}', mapping to UNKNOWN", status);
            return PlayabilityStatus.UNKNOWN;
        }
    }

    private static List<Format> parseAdaptiveFormats(JsonNode root) {
        JsonNode streamingData = root.path("streamingData");
        LOGGER.info("streamingData keys: {}", fieldNames(streamingData));
        LOGGER.info("streamingData.serverAbrStreamingUrl present: {}",
                !streamingData.path("serverAbrStreamingUrl").isMissingNode());

        JsonNode formatsNode = streamingData.path("adaptiveFormats");
        if (formatsNode.isMissingNode() || !formatsNode.isArray()) {
            LOGGER.warn("adaptiveFormats missing or not an array");
            return Collections.emptyList();
        }

        LOGGER.info("adaptiveFormats count: {}", formatsNode.size());
        List<Format> formats = new ArrayList<>(formatsNode.size());
        for (JsonNode f : formatsNode) {
            formats.add(parseFormat(f));
        }
        return Collections.unmodifiableList(formats);
    }

    private static Format parseFormat(JsonNode f) {
        int itag = f.get("itag").asInt();
        String mimeType = f.get("mimeType").asText();
        long bitrate = f.get("bitrate").asLong();

        OptionalInt width = optionalInt(f, "width");
        OptionalInt height = optionalInt(f, "height");
        OptionalInt fps = optionalInt(f, "fps");
        OptionalInt audioSampleRate = optionalIntFromString(f, "audioSampleRate");
        Optional<Long> contentLength = optionalLongFromString(f, "contentLength");

        String url = f.has("url") ? f.get("url").asText() : "";
        String signatureCipher = f.has("signatureCipher")
                ? f.get("signatureCipher").asText() : "";

        LOGGER.info("parseFormat itag={} mime={} keys={} url.len={} cipher.len={}",
                itag, mimeType, fieldNames(f), url.length(), signatureCipher.length());

        return new Format(itag, mimeType, bitrate, width, height, fps,
                audioSampleRate, contentLength, url, signatureCipher);
    }

    private static String fieldNames(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isObject()) {
            return "[]";
        }
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names.toString();
    }

    private static List<CaptionTrack> parseCaptionTracks(JsonNode root) {
        JsonNode tracksNode = root
                .path("captions")
                .path("playerCaptionsTracklistRenderer")
                .path("captionTracks");
        if (tracksNode.isMissingNode() || !tracksNode.isArray()) {
            return Collections.emptyList();
        }

        List<CaptionTrack> tracks = new ArrayList<>(tracksNode.size());
        for (JsonNode t : tracksNode) {
            String baseUrl = t.get("baseUrl").asText();
            LanguageCode languageCode = LanguageCode.of(t.get("languageCode").asText());
            String kind = t.has("kind") ? t.get("kind").asText() : "";
            tracks.add(new CaptionTrack(baseUrl, languageCode, kind));
        }
        return Collections.unmodifiableList(tracks);
    }

    private static List<ThumbnailUrl> parseThumbnails(JsonNode videoDetailsNode) {
        JsonNode thumbsNode = videoDetailsNode.path("thumbnail").path("thumbnails");
        if (thumbsNode.isMissingNode() || !thumbsNode.isArray()) {
            return Collections.emptyList();
        }

        List<ThumbnailUrl> thumbnails = new ArrayList<>(thumbsNode.size());
        for (JsonNode t : thumbsNode) {
            thumbnails.add(new ThumbnailUrl(
                    t.get("url").asText(),
                    t.get("width").asInt(),
                    t.get("height").asInt()));
        }
        return Collections.unmodifiableList(thumbnails);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static JsonNode requireNode(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new InnerTubeParseException("Missing required field: " + field);
        }
        return node;
    }

    private static String requireString(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isTextual()) {
            throw new InnerTubeParseException(
                    "Missing or non-string required field: " + field);
        }
        return node.asText();
    }

    private static OptionalInt optionalInt(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(node.asInt());
    }

    private static OptionalInt optionalIntFromString(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            return OptionalInt.empty();
        }
        // InnerTube sends audioSampleRate as string ("44100") or occasionally number
        String text = node.asText();
        try {
            return OptionalInt.of(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            LOGGER.warn("Unparseable {} value '{}', treating as absent", field, text);
            return OptionalInt.empty();
        }
    }

    private static Optional<Long> optionalLongFromString(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        String text = node.asText();
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException e) {
            LOGGER.warn("Unparseable {} value '{}', treating as absent", field, text);
            return Optional.empty();
        }
    }
}

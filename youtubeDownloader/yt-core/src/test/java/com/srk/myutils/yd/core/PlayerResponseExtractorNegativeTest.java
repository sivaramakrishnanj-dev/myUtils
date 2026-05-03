package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Negative, edge-case, and type-conversion tests for {@link PlayerResponseExtractor}.
 * Covers CT-RESP-N1..N8, PlayabilityStatus mapping, type coercion, and structural edge cases.
 */
class PlayerResponseExtractorNegativeTest {

    // ── CT-RESP-N1: missing videoDetails → InnerTubeParseException ─────

    @Test
    @DisplayName("CT-RESP-N1: missing videoDetails → InnerTubeParseException")
    void extract_givenMissingVideoDetails_throwsInnerTubeParseException() {
        String json = """
                { "playabilityStatus": { "status": "OK" } }""";

        assertThatThrownBy(() -> PlayerResponseExtractor.extract(json))
                .isInstanceOf(InnerTubeParseException.class);
    }

    // ── CT-RESP-N2: missing playabilityStatus → InnerTubeParseException ─

    @Test
    @DisplayName("CT-RESP-N2: missing playabilityStatus → InnerTubeParseException")
    void extract_givenMissingPlayabilityStatus_throwsInnerTubeParseException() {
        String json = """
                {
                  "videoDetails": {
                    "videoId": "dQw4w9WgXcQ",
                    "title": "Test",
                    "isLive": false,
                    "isPrivate": false
                  }
                }""";

        assertThatThrownBy(() -> PlayerResponseExtractor.extract(json))
                .isInstanceOf(InnerTubeParseException.class);
    }

    // ── CT-RESP-N3: empty videoId → InnerTubeParseException ────────────

    @Test
    @DisplayName("CT-RESP-N3: videoDetails.videoId empty string → InnerTubeParseException")
    void extract_givenEmptyVideoId_throwsInnerTubeParseException() {
        String json = """
                {
                  "videoDetails": {
                    "videoId": "",
                    "title": "Test",
                    "isLive": false,
                    "isPrivate": false
                  },
                  "playabilityStatus": { "status": "OK" }
                }""";

        assertThatThrownBy(() -> PlayerResponseExtractor.extract(json))
                .isInstanceOf(InnerTubeParseException.class);
    }

    // ── CT-RESP-N4: isLive as string ──────────────────────────────────
    // Schema says type mismatch, but the extractor is lenient per ADR-0004:
    // Jackson coerces "true" → true. This is a schema-level concern, not
    // an extractor concern. Test documents actual behavior.

    @Test
    @DisplayName("CT-RESP-N4: videoDetails.isLive = \"true\" (string) → coerced to true (extractor lenient)")
    void extract_givenIsLiveAsString_coercedToTrue() {
        String json = """
                {
                  "videoDetails": {
                    "videoId": "dQw4w9WgXcQ",
                    "title": "Test",
                    "isLive": "true",
                    "isPrivate": false
                  },
                  "playabilityStatus": { "status": "OK" }
                }""";

        PlayerResponse r = PlayerResponseExtractor.extract(json);

        assertThat(r.videoDetails().isLive()).isTrue();
    }

    // ── CT-RESP-N5: mimeType = application/octet-stream ────────────────
    // Note: CT-RESP-N5 is a schema-level test (pattern mismatch). At the
    // application level, PlayerResponseExtractor does NOT reject unknown
    // mimeTypes — it parses them faithfully. This test documents that
    // the extractor accepts any mimeType string (schema enforcement is
    // a separate concern per ADR-0004).

    @Test
    @DisplayName("CT-RESP-N5: mimeType application/octet-stream → parsed without error (schema concern)")
    void extract_givenUnknownMimeType_parsesWithoutError() {
        String json = """
                {
                  "videoDetails": {
                    "videoId": "dQw4w9WgXcQ",
                    "title": "Test",
                    "isLive": false,
                    "isPrivate": false
                  },
                  "playabilityStatus": { "status": "OK" },
                  "streamingData": {
                    "adaptiveFormats": [{
                      "itag": 999,
                      "mimeType": "application/octet-stream",
                      "bitrate": 100000,
                      "url": "https://example.com/stream"
                    }]
                  }
                }""";

        PlayerResponse r = PlayerResponseExtractor.extract(json);

        assertThat(r.adaptiveFormats()).hasSize(1);
        assertThat(r.adaptiveFormats().get(0).mimeType()).isEqualTo("application/octet-stream");
    }

    // ── CT-RESP-N6: captionTrack kind = "manual" ───────────────────────
    // At the application level, the extractor stores whatever string is
    // present. isAsr() returns false for "manual". Schema enforcement is separate.

    @Test
    @DisplayName("CT-RESP-N6: captionTrack kind 'manual' → parsed, isAsr() false")
    void extract_givenCaptionKindManual_parsedAndNotAsr() {
        String json = """
                {
                  "videoDetails": {
                    "videoId": "dQw4w9WgXcQ",
                    "title": "Test",
                    "isLive": false,
                    "isPrivate": false
                  },
                  "playabilityStatus": { "status": "OK" },
                  "captions": {
                    "playerCaptionsTracklistRenderer": {
                      "captionTracks": [{
                        "baseUrl": "https://example.com/timedtext",
                        "languageCode": "en",
                        "kind": "manual"
                      }]
                    }
                  }
                }""";

        PlayerResponse r = PlayerResponseExtractor.extract(json);

        assertThat(r.captionTracks()).hasSize(1);
        assertThat(r.captionTracks().get(0).isAsr()).isFalse();
    }

    // ── CT-RESP-N7: empty thumbnails array ─────────────────────────────
    // Schema says minItems:1 but the extractor is lenient per ADR-0004.

    @Test
    @DisplayName("CT-RESP-N7: empty thumbnails array → parsed as empty list")
    void extract_givenEmptyThumbnails_parsedAsEmptyList() {
        String json = """
                {
                  "videoDetails": {
                    "videoId": "dQw4w9WgXcQ",
                    "title": "Test",
                    "isLive": false,
                    "isPrivate": false,
                    "thumbnail": { "thumbnails": [] }
                  },
                  "playabilityStatus": { "status": "OK" }
                }""";

        PlayerResponse r = PlayerResponseExtractor.extract(json);

        assertThat(r.thumbnails()).isNotNull().isEmpty();
    }

    // ── CT-RESP-N8: audioLanguage not BCP-47 ───────────────────────────
    // Schema rejects "English" but the extractor is lenient per ADR-0004.
    // The extractor may either throw (if LanguageCode validates strictly)
    // or degrade. Test documents actual behavior.

    @Test
    @DisplayName("CT-RESP-N8: audioLanguage 'English' (not BCP-47) → InnerTubeParseException or degraded")
    void extract_givenInvalidAudioLanguage_throwsOrDegrades() {
        String json = """
                {
                  "videoDetails": {
                    "videoId": "dQw4w9WgXcQ",
                    "title": "Test",
                    "isLive": false,
                    "isPrivate": false,
                    "audioLanguage": "English"
                  },
                  "playabilityStatus": { "status": "OK" }
                }""";

        // The extractor degrades gracefully: invalid audioLanguage → empty Optional
        // rather than throwing, because audioLanguage is optional metadata.
        PlayerResponse r = PlayerResponseExtractor.extract(json);

        assertThat(r.videoDetails().audioLanguage()).isEmpty();
    }

    // ── PlayabilityStatus mapping ──────────────────────────────────────

    @Nested
    @DisplayName("PlayabilityStatus mapping")
    class PlayabilityStatusMapping {

        @Test
        void extract_givenStatusOk_mapsToOk() {
            assertThat(extractStatus("OK")).isEqualTo(PlayabilityStatus.OK);
        }

        @Test
        void extract_givenStatusUnplayable_mapsToUnplayable() {
            assertThat(extractStatus("UNPLAYABLE")).isEqualTo(PlayabilityStatus.UNPLAYABLE);
        }

        @Test
        void extract_givenStatusLiveStreamOffline_mapsToLiveStreamOffline() {
            assertThat(extractStatus("LIVE_STREAM_OFFLINE")).isEqualTo(PlayabilityStatus.LIVE_STREAM_OFFLINE);
        }

        @Test
        void extract_givenStatusLoginRequired_mapsToLoginRequired() {
            assertThat(extractStatus("LOGIN_REQUIRED")).isEqualTo(PlayabilityStatus.LOGIN_REQUIRED);
        }

        @Test
        void extract_givenStatusError_mapsToError() {
            assertThat(extractStatus("ERROR")).isEqualTo(PlayabilityStatus.ERROR);
        }

        @Test
        void extract_givenStatusAgeVerificationRequired_mapsToAgeVerificationRequired() {
            assertThat(extractStatus("AGE_VERIFICATION_REQUIRED")).isEqualTo(PlayabilityStatus.AGE_VERIFICATION_REQUIRED);
        }

        @Test
        @DisplayName("unrecognized status → UNKNOWN (with WARN log)")
        void extract_givenUnrecognizedStatus_mapsToUnknown() {
            assertThat(extractStatus("UNRECOGNIZED_NEW_STATUS")).isEqualTo(PlayabilityStatus.UNKNOWN);
        }

        private PlayabilityStatus extractStatus(String status) {
            String json = """
                    {
                      "videoDetails": {
                        "videoId": "dQw4w9WgXcQ",
                        "title": "Test",
                        "isLive": false,
                        "isPrivate": false
                      },
                      "playabilityStatus": { "status": "%s" }
                    }""".formatted(status);
            return PlayerResponseExtractor.extract(json).playabilityStatus();
        }
    }

    // ── Type-conversion edge cases ─────────────────────────────────────

    @Nested
    @DisplayName("Type-conversion edge cases")
    class TypeConversion {

        @Test
        @DisplayName("audioSampleRate as number 44100 → OptionalInt.of(44100)")
        void extract_givenAudioSampleRateAsNumber_parsesToOptionalInt() {
            String json = minimalWithFormat("""
                    "itag": 140, "mimeType": "audio/mp4; codecs=\\"mp4a.40.2\\"",
                    "bitrate": 130000, "audioSampleRate": 44100,
                    "url": "https://example.com/stream"
                    """);

            PlayerResponse r = PlayerResponseExtractor.extract(json);

            assertThat(r.adaptiveFormats().get(0).audioSampleRate()).hasValue(44100);
        }

        @Test
        @DisplayName("audioSampleRate absent → OptionalInt.empty()")
        void extract_givenAudioSampleRateAbsent_returnsEmpty() {
            String json = minimalWithFormat("""
                    "itag": 140, "mimeType": "audio/mp4; codecs=\\"mp4a.40.2\\"",
                    "bitrate": 130000,
                    "url": "https://example.com/stream"
                    """);

            PlayerResponse r = PlayerResponseExtractor.extract(json);

            assertThat(r.adaptiveFormats().get(0).audioSampleRate()).isEmpty();
        }

        @Test
        @DisplayName("audioSampleRate malformed 'foo' → OptionalInt.empty() (degraded)")
        void extract_givenAudioSampleRateMalformed_returnsEmpty() {
            String json = minimalWithFormat("""
                    "itag": 140, "mimeType": "audio/mp4; codecs=\\"mp4a.40.2\\"",
                    "bitrate": 130000, "audioSampleRate": "foo",
                    "url": "https://example.com/stream"
                    """);

            PlayerResponse r = PlayerResponseExtractor.extract(json);

            assertThat(r.adaptiveFormats().get(0).audioSampleRate()).isEmpty();
        }

        @Test
        @DisplayName("contentLength absent → Optional.empty()")
        void extract_givenContentLengthAbsent_returnsEmpty() {
            String json = minimalWithFormat("""
                    "itag": 140, "mimeType": "audio/mp4; codecs=\\"mp4a.40.2\\"",
                    "bitrate": 130000,
                    "url": "https://example.com/stream"
                    """);

            PlayerResponse r = PlayerResponseExtractor.extract(json);

            assertThat(r.adaptiveFormats().get(0).contentLength()).isEmpty();
        }

        @Test
        @DisplayName("contentLength as string '95000000' → Optional.of(95000000L)")
        void extract_givenContentLengthAsString_parsesToLong() {
            String json = minimalWithFormat("""
                    "itag": 137, "mimeType": "video/mp4; codecs=\\"avc1.640028\\"",
                    "bitrate": 4500000, "contentLength": "95000000",
                    "url": "https://example.com/stream"
                    """);

            PlayerResponse r = PlayerResponseExtractor.extract(json);

            assertThat(r.adaptiveFormats().get(0).contentLength()).hasValue(95_000_000L);
        }
    }

    // ── Structural edge cases ──────────────────────────────────────────

    @Nested
    @DisplayName("Structural edge cases")
    class StructuralEdgeCases {

        @Test
        @DisplayName("empty streamingData → formats is empty list (not null)")
        void extract_givenEmptyStreamingData_formatsIsEmptyList() {
            String json = """
                    {
                      "videoDetails": {
                        "videoId": "dQw4w9WgXcQ",
                        "title": "Test",
                        "isLive": false,
                        "isPrivate": false
                      },
                      "playabilityStatus": { "status": "OK" },
                      "streamingData": {}
                    }""";

            PlayerResponse r = PlayerResponseExtractor.extract(json);

            assertThat(r.adaptiveFormats()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("missing streamingData entirely → formats is empty list")
        void extract_givenNoStreamingData_formatsIsEmptyList() {
            String json = """
                    {
                      "videoDetails": {
                        "videoId": "dQw4w9WgXcQ",
                        "title": "Test",
                        "isLive": false,
                        "isPrivate": false
                      },
                      "playabilityStatus": { "status": "OK" }
                    }""";

            PlayerResponse r = PlayerResponseExtractor.extract(json);

            assertThat(r.adaptiveFormats()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("missing captions → captionTracks is empty list (not null)")
        void extract_givenNoCaptions_captionTracksIsEmptyList() {
            String json = """
                    {
                      "videoDetails": {
                        "videoId": "dQw4w9WgXcQ",
                        "title": "Test",
                        "isLive": false,
                        "isPrivate": false
                      },
                      "playabilityStatus": { "status": "OK" }
                    }""";

            PlayerResponse r = PlayerResponseExtractor.extract(json);

            assertThat(r.captionTracks()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("missing thumbnail object → thumbnails is empty list")
        void extract_givenNoThumbnailObject_thumbnailsIsEmptyList() {
            String json = """
                    {
                      "videoDetails": {
                        "videoId": "dQw4w9WgXcQ",
                        "title": "Test",
                        "isLive": false,
                        "isPrivate": false
                      },
                      "playabilityStatus": { "status": "OK" }
                    }""";

            PlayerResponse r = PlayerResponseExtractor.extract(json);

            assertThat(r.thumbnails()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("malformed JSON → InnerTubeParseException wrapping JsonProcessingException")
        void extract_givenMalformedJson_throwsInnerTubeParseException() {
            assertThatThrownBy(() -> PlayerResponseExtractor.extract("{not valid json"))
                    .isInstanceOf(InnerTubeParseException.class)
                    .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        }

        @Test
        @DisplayName("null input → IllegalArgumentException (Jackson rejects null content)")
        void extract_givenNullInput_throwsException() {
            assertThatThrownBy(() -> PlayerResponseExtractor.extract(null))
                    .isInstanceOfAny(InnerTubeParseException.class,
                            NullPointerException.class,
                            IllegalArgumentException.class);
        }

        @Test
        @DisplayName("empty string → InnerTubeParseException")
        void extract_givenEmptyString_throwsInnerTubeParseException() {
            assertThatThrownBy(() -> PlayerResponseExtractor.extract(""))
                    .isInstanceOf(InnerTubeParseException.class);
        }

        @Test
        @DisplayName("videoDetails.audioLanguage absent → Optional.empty()")
        void extract_givenNoAudioLanguage_returnsEmptyOptional() {
            String json = """
                    {
                      "videoDetails": {
                        "videoId": "dQw4w9WgXcQ",
                        "title": "Test",
                        "isLive": false,
                        "isPrivate": false
                      },
                      "playabilityStatus": { "status": "OK" }
                    }""";

            PlayerResponse r = PlayerResponseExtractor.extract(json);

            assertThat(r.videoDetails().audioLanguage()).isEmpty();
        }

        @Test
        @DisplayName("format with signatureCipher and no url → hasCipher() true, url empty")
        void extract_givenFormatWithCipherNoUrl_hasCipherTrue() {
            String json = minimalWithFormat("""
                    "itag": 137, "mimeType": "video/mp4; codecs=\\"avc1.640028\\"",
                    "bitrate": 4500000,
                    "signatureCipher": "s=SCRAMBLED&url=https%3A%2F%2Fexample.com"
                    """);

            PlayerResponse r = PlayerResponseExtractor.extract(json);

            assertThat(r.adaptiveFormats().get(0).hasCipher()).isTrue();
            assertThat(r.adaptiveFormats().get(0).url()).isEmpty();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Builds a minimal valid JSON with one format entry containing the given fields.
     */
    private static String minimalWithFormat(String formatFields) {
        return """
                {
                  "videoDetails": {
                    "videoId": "dQw4w9WgXcQ",
                    "title": "Test",
                    "isLive": false,
                    "isPrivate": false
                  },
                  "playabilityStatus": { "status": "OK" },
                  "streamingData": {
                    "adaptiveFormats": [{ %s }]
                  }
                }""".formatted(formatFields);
    }
}

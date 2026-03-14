package com.srk.myutils.texttospeech;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts text content to speech audio using AWS Polly.
 * <p>
 * Uses the Generative engine with British English voice Amy.
 * Polly limits input to 3000 characters per request, so longer
 * content is automatically chunked and the resulting audio
 * streams are concatenated.
 */
public class TextToSpeechConverter {

    private static final int MAX_CHARS_PER_REQUEST = 3000;
    private static final Engine ENGINE = Engine.GENERATIVE;
    private static final LanguageCode LANGUAGE = LanguageCode.EN_GB;
    private static final VoiceId VOICE = VoiceId.AMY;
    private static final OutputFormat OUTPUT_FORMAT = OutputFormat.MP3;

    private final PollyClient polly;

    public TextToSpeechConverter(String profileName) {
        this.polly = PollyClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(ProfileCredentialsProvider.create(profileName))
                .build();
    }

    /**
     * Converts the given text to an audio file at the specified path.
     */
    public void convert(String text, Path outputPath) throws IOException {
        List<String> chunks = chunk(text);
        List<InputStream> streams = new ArrayList<>();

        for (String chunk : chunks) {
            SynthesizeSpeechRequest request = SynthesizeSpeechRequest.builder()
                    .text(chunk)
                    .engine(ENGINE)
                    .languageCode(LANGUAGE)
                    .voiceId(VOICE)
                    .outputFormat(OUTPUT_FORMAT)
                    .build();

            ResponseInputStream<SynthesizeSpeechResponse> response = polly.synthesizeSpeech(request);
            streams.add(response);
        }

        try (InputStream combined = new SequenceInputStream(Collections.enumeration(streams))) {
            Files.copy(combined, outputPath);
        }
    }

    /**
     * Splits text into chunks that respect the Polly character limit,
     * breaking at sentence boundaries when possible.
     */
    private List<String> chunk(String text) {
        if (text.length() <= MAX_CHARS_PER_REQUEST) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHARS_PER_REQUEST, text.length());
            if (end < text.length()) {
                int sentenceBreak = text.lastIndexOf(". ", end);
                if (sentenceBreak > start) {
                    end = sentenceBreak + 1;
                }
            }
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }
}

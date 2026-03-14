package com.srk.myutils.speechtotext;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.AudioEvent;
import software.amazon.awssdk.services.transcribestreaming.model.AudioStream;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;
import software.amazon.awssdk.services.transcribestreaming.model.MediaEncoding;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionRequest;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionResponseHandler;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

import javax.sound.sampled.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Captures microphone audio and performs real-time speech-to-text
 * using AWS Transcribe Streaming (en-GB).
 *
 * <p>Usage:
 * <pre>
 * SpeechToTextConverter converter = new SpeechToTextConverter("my-aws-profile");
 * converter.start();
 * // ... wait for user signal ...
 * String transcript = converter.stop();
 * </pre>
 */
public class SpeechToTextConverter {

    private static final int SAMPLE_RATE = 16000;
    private static final AudioFormat MIC_FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private final TranscribeStreamingAsyncClient client;
    private final List<String> completedSegments = new CopyOnWriteArrayList<>();
    private volatile boolean listening;
    private TargetDataLine micLine;
    private CompletableFuture<Void> future;

    public SpeechToTextConverter(String profileName) {
        this.client = TranscribeStreamingAsyncClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(ProfileCredentialsProvider.create(profileName))
                .build();
    }

    /**
     * Opens the microphone and begins streaming audio to Transcribe.
     * Partial results are printed to stdout in real-time.
     * Call {@link #stop()} to end transcription and get the final text.
     */
    public void start() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, MIC_FORMAT);
        micLine = (TargetDataLine) AudioSystem.getLine(info);
        micLine.open(MIC_FORMAT);
        micLine.start();
        listening = true;

        StartStreamTranscriptionRequest request = StartStreamTranscriptionRequest.builder()
                .languageCode(LanguageCode.EN_GB)
                .mediaEncoding(MediaEncoding.PCM)
                .mediaSampleRateHertz(SAMPLE_RATE)
                .build();

        future = client.startStreamTranscription(request, createAudioPublisher(), createResponseHandler());
    }

    /**
     * Stops the microphone, waits for the transcription session to complete,
     * and returns the final assembled transcript.
     */
    public String stop() {
        listening = false;
        if (micLine != null) {
            micLine.stop();
            micLine.close();
        }
        if (future != null) {
            future.join();
        }
        return String.join(" ", completedSegments).trim();
    }

    private Publisher<AudioStream> createAudioPublisher() {
        return subscriber -> {
            AtomicBoolean started = new AtomicBoolean(false);
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    if (started.compareAndSet(false, true)) {
                        new Thread(() -> {
                            try {
                                byte[] buf = new byte[1024];
                                while (listening) {
                                    int read = micLine.read(buf, 0, buf.length);
                                    if (read > 0) {
                                        AudioEvent event = AudioEvent.builder()
                                                .audioChunk(SdkBytes.fromByteArray(Arrays.copyOf(buf, read)))
                                                .build();
                                        subscriber.onNext(event);
                                    }
                                }
                            } finally {
                                subscriber.onNext(AudioEvent.builder().audioChunk(SdkBytes.fromByteArray(new byte[0])).build());
                                subscriber.onComplete();
                            }
                        }, "mic-reader").start();
                    }
                }

                @Override
                public void cancel() {
                    listening = false;
                }
            });
        };
    }

    private StartStreamTranscriptionResponseHandler createResponseHandler() {
        return StartStreamTranscriptionResponseHandler.builder()
                .onEventStream(publisher -> publisher.subscribe(event -> {
                    if (event instanceof TranscriptEvent te) {
                        te.transcript().results().forEach(result -> {
                            if (result.alternatives().isEmpty()) return;
                            String text = result.alternatives().get(0).transcript();
                            if (result.isPartial()) {
                                System.out.print("\r  " + text + "   ");
                            } else {
                                System.out.println("\r  " + text);
                                completedSegments.add(text);
                            }
                        });
                    }
                }))
                .build();
    }
}

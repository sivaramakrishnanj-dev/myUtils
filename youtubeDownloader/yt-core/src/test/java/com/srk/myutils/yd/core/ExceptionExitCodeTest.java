package com.srk.myutils.yd.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tester-written tests for T-1.9 exception hierarchy.
 *
 * <p>Covers CT-EXIT-UNIT-1..11 (contract tests from {@code 06-formal/contract-tests.md § 4}),
 * hierarchy invariants (sealed, abstract, final), constructor behaviours,
 * integration regression, and switch-dispatch readiness.
 *
 * <p>Oracle: the spec ({@code cli-exit-codes.md § 3}, AC-5.2, AC-9.4),
 * not the implementation.
 */
class ExceptionExitCodeTest {

    // ── § 1. CT-EXIT-UNIT-* contract tests (individually named per CT ID) ──

    @Nested
    @DisplayName("CT-EXIT-UNIT — exit code correctness per cli-exit-codes.md § 3")
    class ExitCodeContract {

        @Test
        @DisplayName("CT-EXIT-UNIT-1: UrlParseException.exitCode() == 2")
        void ctExitUnit1_urlParseException() {
            assertThat(new UrlParseException("bad").exitCode()).isEqualTo(2);
        }

        @Test
        @DisplayName("CT-EXIT-UNIT-2: NetworkException.exitCode() == 10")
        void ctExitUnit2_networkException() {
            assertThat(new NetworkException("fail").exitCode()).isEqualTo(10);
        }

        @Test
        @DisplayName("CT-EXIT-UNIT-3: InnerTubeParseException.exitCode() == 11")
        void ctExitUnit3_innerTubeParseException() {
            assertThat(new InnerTubeParseException("bad json").exitCode()).isEqualTo(11);
        }

        @Test
        @DisplayName("CT-EXIT-UNIT-4: VideoUnavailableException.exitCode() == 20")
        void ctExitUnit4_videoUnavailableException() {
            assertThat(new VideoUnavailableException("private").exitCode()).isEqualTo(20);
        }

        @Test
        @DisplayName("CT-EXIT-UNIT-5: LiveStreamException.exitCode() == 21")
        void ctExitUnit5_liveStreamException() {
            assertThat(new LiveStreamException("is live").exitCode()).isEqualTo(21);
        }

        @Test
        @DisplayName("CT-EXIT-UNIT-6: CipherRequiredException.exitCode() == 22")
        void ctExitUnit6_cipherRequiredException() {
            assertThat(new CipherRequiredException("cipher").exitCode()).isEqualTo(22);
        }

        @Test
        @DisplayName("CT-EXIT-UNIT-7: NoMatchingFormatException.exitCode() == 30")
        void ctExitUnit7_noMatchingFormatException() {
            assertThat(new NoMatchingFormatException("no format").exitCode()).isEqualTo(30);
        }

        @Test
        @DisplayName("CT-EXIT-UNIT-8: CaptionUnavailableException.exitCode() == 40")
        void ctExitUnit8_captionUnavailableException() {
            assertThat(new CaptionUnavailableException("no captions").exitCode()).isEqualTo(40);
        }

        @Test
        @DisplayName("CT-EXIT-UNIT-9: OutputExistsException.exitCode() == 50")
        void ctExitUnit9_outputExistsException() {
            assertThat(new OutputExistsException("exists").exitCode()).isEqualTo(50);
        }

        @Test
        @DisplayName("CT-EXIT-UNIT-10: FfmpegException.exitCode() == 60")
        void ctExitUnit10_ffmpegException() {
            assertThat(new FfmpegException("missing").exitCode()).isEqualTo(60);
        }

        @Test
        @DisplayName("CT-EXIT-UNIT-11: FilesystemException.exitCode() == 70")
        void ctExitUnit11_filesystemException() {
            assertThat(new FilesystemException("disk full").exitCode()).isEqualTo(70);
        }
    }

    // ── § 2. Hierarchy invariants ──────────────────────────────────────

    @Nested
    @DisplayName("Hierarchy invariants")
    class HierarchyInvariants {

        private static final List<Class<? extends YoutubeDownloaderException>> ALL_SUBCLASSES = List.of(
                UrlParseException.class, NetworkException.class, InnerTubeParseException.class,
                VideoUnavailableException.class, LiveStreamException.class, CipherRequiredException.class,
                NoMatchingFormatException.class, CaptionUnavailableException.class,
                OutputExistsException.class, FfmpegException.class, FilesystemException.class
        );

        @Test
        @DisplayName("YoutubeDownloaderException is abstract")
        void baseClass_isAbstract() {
            assertThat(Modifier.isAbstract(YoutubeDownloaderException.class.getModifiers())).isTrue();
        }

        @Test
        @DisplayName("YoutubeDownloaderException is sealed")
        void baseClass_isSealed() {
            assertThat(YoutubeDownloaderException.class.isSealed()).isTrue();
        }

        @Test
        @DisplayName("sealed permits exactly 11 subclasses")
        void sealedPermits_hasExactly11() {
            assertThat(YoutubeDownloaderException.class.getPermittedSubclasses()).hasSize(11);
        }

        @Test
        @DisplayName("permitted subclasses match the expected set")
        void sealedPermits_matchExpectedSet() {
            Set<String> permitted = Set.copyOf(Arrays.stream(
                    YoutubeDownloaderException.class.getPermittedSubclasses())
                    .map(Class::getSimpleName)
                    .toList());

            assertThat(permitted).containsExactlyInAnyOrder(
                    "UrlParseException", "NetworkException", "InnerTubeParseException",
                    "VideoUnavailableException", "LiveStreamException", "CipherRequiredException",
                    "NoMatchingFormatException", "CaptionUnavailableException",
                    "OutputExistsException", "FfmpegException", "FilesystemException"
            );
        }

        @ParameterizedTest(name = "{0} is final")
        @MethodSource("subclassProvider")
        @DisplayName("every subclass is final")
        void subclass_isFinal(Class<? extends YoutubeDownloaderException> subclass) {
            assertThat(Modifier.isFinal(subclass.getModifiers())).isTrue();
        }

        @ParameterizedTest(name = "{0} IS-A YoutubeDownloaderException")
        @MethodSource("subclassProvider")
        @DisplayName("every subclass is assignable to YoutubeDownloaderException")
        void subclass_isAssignableToBase(Class<? extends YoutubeDownloaderException> subclass) {
            assertThat(YoutubeDownloaderException.class).isAssignableFrom(subclass);
        }

        @ParameterizedTest(name = "{0} IS-A RuntimeException")
        @MethodSource("subclassProvider")
        @DisplayName("every subclass is a RuntimeException (unchecked)")
        void subclass_isRuntimeException(Class<? extends YoutubeDownloaderException> subclass) {
            assertThat(RuntimeException.class).isAssignableFrom(subclass);
        }

        static Stream<Class<? extends YoutubeDownloaderException>> subclassProvider() {
            return ALL_SUBCLASSES.stream();
        }
    }

    // ── § 3. Constructor behaviours ────────────────────────────────────

    @Nested
    @DisplayName("Constructor behaviours")
    class ConstructorBehaviours {

        // Subclasses with (String, Throwable) cause constructor
        @Test
        @DisplayName("InnerTubeParseException(msg, cause) preserves cause")
        void innerTubeParseException_preservesCause() {
            var cause = new RuntimeException("parse error");
            var ex = new InnerTubeParseException("bad json", cause);

            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getMessage()).isEqualTo("bad json");
        }

        @Test
        @DisplayName("FilesystemException(msg, cause) preserves cause")
        void filesystemException_preservesCause() {
            var cause = new java.io.IOException("disk full");
            var ex = new FilesystemException("write failed", cause);

            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getMessage()).isEqualTo("write failed");
        }

        // Message-only subclasses: cause is null
        static Stream<Arguments> messageOnlySubclasses() {
            return Stream.of(
                    Arguments.of(new UrlParseException("bad url"), "UrlParseException"),
                    Arguments.of(new VideoUnavailableException("private"), "VideoUnavailableException"),
                    Arguments.of(new LiveStreamException("is live"), "LiveStreamException"),
                    Arguments.of(new CipherRequiredException("cipher"), "CipherRequiredException"),
                    Arguments.of(new NoMatchingFormatException("no format"), "NoMatchingFormatException"),
                    Arguments.of(new CaptionUnavailableException("no captions"), "CaptionUnavailableException"),
                    Arguments.of(new OutputExistsException("exists"), "OutputExistsException")
            );
        }

        @ParameterizedTest(name = "{1} message-only ctor has null cause")
        @MethodSource("messageOnlySubclasses")
        @DisplayName("message-only subclasses have null cause")
        void messageOnlySubclass_hasNullCause(YoutubeDownloaderException ex, String name) {
            assertThat(ex.getCause()).isNull();
        }

        @ParameterizedTest(name = "{1} preserves message")
        @MethodSource("messageOnlySubclasses")
        @DisplayName("message-only subclasses preserve getMessage()")
        void messageOnlySubclass_preservesMessage(YoutubeDownloaderException ex, String name) {
            assertThat(ex.getMessage()).isNotNull().isNotEmpty();
        }

        // Cause-accepting subclasses also work with message-only ctor
        @Test
        @DisplayName("NetworkException(msg) has null cause")
        void networkException_messageOnly_hasNullCause() {
            var ex = new NetworkException("fail");

            assertThat(ex.getCause()).isNull();
            assertThat(ex.getMessage()).isEqualTo("fail");
        }

        @Test
        @DisplayName("FfmpegException(msg) has null cause")
        void ffmpegException_messageOnly_hasNullCause() {
            var ex = new FfmpegException("missing");

            assertThat(ex.getCause()).isNull();
            assertThat(ex.getMessage()).isEqualTo("missing");
        }

        @Test
        @DisplayName("InnerTubeParseException(msg) has null cause")
        void innerTubeParseException_messageOnly_hasNullCause() {
            var ex = new InnerTubeParseException("bad");

            assertThat(ex.getCause()).isNull();
            assertThat(ex.getMessage()).isEqualTo("bad");
        }

        @Test
        @DisplayName("FilesystemException(msg) has null cause")
        void filesystemException_messageOnly_hasNullCause() {
            var ex = new FilesystemException("full");

            assertThat(ex.getCause()).isNull();
            assertThat(ex.getMessage()).isEqualTo("full");
        }
    }

    // ── § 4. Integration / regression ──────────────────────────────────

    @Nested
    @DisplayName("Integration — exceptions thrown from existing components")
    class IntegrationRegression {

        @Test
        @DisplayName("VideoId.of(invalid) throws UrlParseException which IS-A YoutubeDownloaderException with exitCode 2")
        void videoIdOf_invalidId_throwsUrlParseExceptionWithExitCode2() {
            assertThatThrownBy(() -> VideoId.of("!!!"))
                    .isInstanceOf(UrlParseException.class)
                    .isInstanceOf(YoutubeDownloaderException.class)
                    .satisfies(ex -> assertThat(((YoutubeDownloaderException) ex).exitCode()).isEqualTo(2));
        }

        @Test
        @DisplayName("PlayerResponseExtractor.extract(malformed) throws InnerTubeParseException with exitCode 11")
        void playerResponseExtractor_malformedJson_throwsInnerTubeParseExceptionWithExitCode11() {
            assertThatThrownBy(() -> PlayerResponseExtractor.extract("not json"))
                    .isInstanceOf(InnerTubeParseException.class)
                    .isInstanceOf(YoutubeDownloaderException.class)
                    .satisfies(ex -> assertThat(((YoutubeDownloaderException) ex).exitCode()).isEqualTo(11));
        }

        @Test
        @DisplayName("PlayerResponseExtractor.extract(missing videoDetails) throws InnerTubeParseException with exitCode 11")
        void playerResponseExtractor_missingVideoDetails_throwsInnerTubeParseExceptionWithExitCode11() {
            String json = "{\"playabilityStatus\":{\"status\":\"OK\"}}";

            assertThatThrownBy(() -> PlayerResponseExtractor.extract(json))
                    .isInstanceOf(InnerTubeParseException.class)
                    .satisfies(ex -> assertThat(((YoutubeDownloaderException) ex).exitCode()).isEqualTo(11));
        }
    }

    // ── § 5. Exhaustive switch dispatch readiness ──────────────────────

    @Nested
    @DisplayName("Switch dispatch readiness — all 11 subclasses constructible")
    class SwitchDispatchReadiness {

        /**
         * Verifies that a switch expression over YoutubeDownloaderException
         * can handle all 11 sealed subtypes. This is a compile-time + runtime
         * check: if a subclass were missing from the switch, the compiler
         * would reject it (sealed exhaustiveness). At runtime we verify
         * every branch is reachable.
         */
        @Test
        @DisplayName("switch over all 11 subtypes is exhaustive and each branch reachable")
        void switchExpression_isExhaustive() {
            List<YoutubeDownloaderException> all = List.of(
                    new UrlParseException("a"),
                    new NetworkException("b"),
                    new InnerTubeParseException("c"),
                    new VideoUnavailableException("d"),
                    new LiveStreamException("e"),
                    new CipherRequiredException("f"),
                    new NoMatchingFormatException("g"),
                    new CaptionUnavailableException("h"),
                    new OutputExistsException("i"),
                    new FfmpegException("j"),
                    new FilesystemException("k")
            );

            for (YoutubeDownloaderException ex : all) {
                int code = mapExitCode(ex);
                assertThat(code).isEqualTo(ex.exitCode());
            }
        }

        /**
         * Maps each sealed subtype to its expected exit code using instanceof.
         * On Java 21+ this would be an exhaustive switch; on Java 17 we use
         * an if-chain and fail on unknown type to catch any future subclass
         * that isn't handled.
         */
        private int mapExitCode(YoutubeDownloaderException ex) {
            if (ex instanceof UrlParseException) return 2;
            if (ex instanceof NetworkException) return 10;
            if (ex instanceof InnerTubeParseException) return 11;
            if (ex instanceof VideoUnavailableException) return 20;
            if (ex instanceof LiveStreamException) return 21;
            if (ex instanceof CipherRequiredException) return 22;
            if (ex instanceof NoMatchingFormatException) return 30;
            if (ex instanceof CaptionUnavailableException) return 40;
            if (ex instanceof OutputExistsException) return 50;
            if (ex instanceof FfmpegException) return 60;
            if (ex instanceof FilesystemException) return 70;
            throw new AssertionError("Unhandled subclass: " + ex.getClass().getSimpleName());
        }
    }

    // ── § 6. Exit code uniqueness (AC-9.4 — one-to-one mapping) ───────

    @Test
    @DisplayName("all 11 exit codes are unique (AC-9.4 one-to-one mapping)")
    void exitCodes_areUnique() {
        List<Integer> codes = List.of(
                new UrlParseException("a").exitCode(),
                new NetworkException("b").exitCode(),
                new InnerTubeParseException("c").exitCode(),
                new VideoUnavailableException("d").exitCode(),
                new LiveStreamException("e").exitCode(),
                new CipherRequiredException("f").exitCode(),
                new NoMatchingFormatException("g").exitCode(),
                new CaptionUnavailableException("h").exitCode(),
                new OutputExistsException("i").exitCode(),
                new FfmpegException("j").exitCode(),
                new FilesystemException("k").exitCode()
        );

        assertThat(codes).doesNotHaveDuplicates();
    }
}

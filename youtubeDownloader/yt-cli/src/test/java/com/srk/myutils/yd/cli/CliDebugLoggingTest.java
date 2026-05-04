package com.srk.myutils.yd.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test: {@code --debug} triggers SLF4J SimpleLogger
 * default level to {@code debug} (AC-10.5).
 */
class CliDebugLoggingTest {

    private static final String PROP = "org.slf4j.simpleLogger.defaultLogLevel";

    @AfterEach
    void resetProperty() {
        System.clearProperty(PROP);
    }

    @Test
    void configureLogging_givenDebugFlag_setsDefaultLevelToDebug() {
        Cli.configureLogging(new String[]{"--debug", "https://www.youtube.com/watch?v=abc123"});
        assertThat(System.getProperty(PROP)).isEqualTo("debug");
    }
}

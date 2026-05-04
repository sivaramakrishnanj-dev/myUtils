package com.srk.myutils.yd.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link ErrorMapper} — happy-path only.
 * Exhaustive coverage (all 11 categories + unexpected exception) is the tester's job.
 */
class ErrorMapperTest {

    @Test
    void map_givenUrlParseException_returnsArgsCategory() {
        ErrorReport report = ErrorMapper.map(new UrlParseException("bad url"));
        assertThat(report.exitCode()).isEqualTo(2);
        assertThat(report.message()).isEqualTo("Error: args: bad url");
    }
}

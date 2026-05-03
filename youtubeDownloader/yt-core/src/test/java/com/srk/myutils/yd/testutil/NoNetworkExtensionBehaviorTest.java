package com.srk.myutils.yd.testutil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive behavior tests for {@link NoNetworkExtension}.
 *
 * <p>Covers AC-11.3 (unit tests fail on network I/O) and AC-11.4 (clear message
 * naming the component). The extension is auto-registered via
 * {@code META-INF/services} and active for this test class.
 */
class NoNetworkExtensionBehaviorTest {

    // ── AC-11.3: outbound TCP blocked ──────────────────────────────

    @Test
    @DisplayName("AC-11.3: new Socket(host, port) throws SecurityException")
    void outboundTcp_blocked() {
        assertThatThrownBy(() -> new Socket("example.com", 80))
                .isInstanceOf(SecurityException.class);
    }

    // ── AC-11.4: failure message names host and port ───────────────

    @Test
    @DisplayName("AC-11.4: SecurityException message contains host — DNS intercept fires first with port=-1")
    void outboundTcp_messageNamesHost() {
        // Note: new Socket(host, port) resolves DNS first, which triggers
        // checkConnect(host, -1). The SecurityManager blocks at DNS level,
        // so the actual target port (443) never appears in the message.
        // The HOST is always present, satisfying AC-11.4's "naming the component".
        assertThatThrownBy(() -> new Socket("httpbin.org", 443))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("httpbin.org")
                .hasMessageContaining("network I/O is forbidden in unit tests");
    }

    // ── AC-11.3: NIO SocketChannel blocked ─────────────────────────

    @Test
    @DisplayName("AC-11.3: SocketChannel.open(InetSocketAddress) throws SecurityException")
    void nioSocketChannel_blocked() {
        assertThatThrownBy(() -> SocketChannel.open(new InetSocketAddress("example.com", 80)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("example.com");
    }

    // ── AC-11.3: inbound ServerSocket.bind blocked ─────────────────

    @Test
    @DisplayName("AC-11.3: ServerSocket.bind throws SecurityException — prevents local mock servers")
    void serverSocketBind_blocked() {
        assertThatThrownBy(() -> {
            try (ServerSocket ss = new ServerSocket()) {
                ss.bind(new InetSocketAddress(0));
            }
        }).isInstanceOf(SecurityException.class)
                .hasMessageContaining("network I/O is forbidden in unit tests");
    }

    // ── DNS behavior documentation ─────────────────────────────────

    @Test
    @DisplayName("AC-11.3: InetAddress.getByName triggers checkConnect with port=-1 — blocked")
    void dnsLookup_blocked() {
        // checkConnect(host, -1) is called for DNS resolution.
        // The SecurityManager blocks ALL checkConnect calls regardless of port,
        // which means DNS is also blocked. This is stricter than strictly necessary
        // but consistent with AC-11.3 ("makes a network call").
        assertThatThrownBy(() -> InetAddress.getByName("example.com"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("example.com");
    }

    // ── Filesystem NOT blocked ─────────────────────────────────────

    @Test
    @DisplayName("Filesystem I/O is not blocked by the SecurityManager")
    void filesystemWrite_notBlocked(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test-output.txt");

        Files.writeString(file, "hello from unit test");

        assertThat(file).exists().hasContent("hello from unit test");
    }

    // ── Escape-hatch: verified via POM config analysis ─────────────
    //
    // The escape-hatch (yt.test.allow-network=true) is set ONLY in the
    // Failsafe plugin config under the integration profile. Surefire does
    // NOT receive this property even under -P integration, which is correct:
    // unit tests should ALWAYS be blocked from network I/O.
    //
    // Failsafe-run integration tests (tagged @Tag("integration")) get the
    // property and skip SecurityManager installation. This matches AC-11.3:
    // "Network access in tests is enabled only in [...] integration profile."
    //
    // We cannot directly test the escape-hatch in a Surefire-run unit test
    // (setting the property here would disable the very guard we're testing).
    // The wiring is verified by reading the POM config above.
}

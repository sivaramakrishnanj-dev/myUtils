package com.srk.myutils.yd.testutil;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.security.Permission;

/**
 * JUnit 5 extension that forbids network socket operations during unit tests.
 *
 * <p>Satisfies AC-11.3 (unit tests SHALL fail if they make a network call) and
 * AC-11.4 (failure message SHALL name the component that reached out).
 *
 * <p>Installs a {@link SecurityManager} that throws {@link SecurityException} on
 * {@code checkConnect} and {@code checkListen}. The extension is registered globally
 * via {@code META-INF/services/org.junit.jupiter.api.extension.Extension} and
 * auto-detection in {@code junit-platform.properties}.
 *
 * <p>The extension is disabled when the system property {@code yt.test.allow-network}
 * is set to {@code true}, which the {@code integration} Maven profile sets so that
 * integration tests can open sockets.
 *
 * <p><strong>Note:</strong> {@link SecurityManager} is deprecated for removal (JEP 411)
 * but fully functional on Java 17. Acceptable for MVP; revisit if the project moves to
 * Java 21+.
 */
@SuppressWarnings("removal") // SecurityManager deprecated-for-removal in Java 17 (JEP 411)
public class NoNetworkExtension implements BeforeAllCallback {

    private static volatile boolean installed;

    @Override
    public void beforeAll(ExtensionContext context) {
        if ("true".equalsIgnoreCase(System.getProperty("yt.test.allow-network"))) {
            return;
        }
        if (installed) {
            return;
        }
        System.setSecurityManager(new NoNetworkSecurityManager());
        installed = true;
    }

    /**
     * SecurityManager that blocks all socket connect and listen operations.
     * All other permissions are allowed so that the test JVM operates normally.
     */
    static final class NoNetworkSecurityManager extends SecurityManager {

        @Override
        public void checkConnect(String host, int port) {
            throw new SecurityException(
                    "Unit test attempted network connection to " + host + ":" + port
                            + " — network I/O is forbidden in unit tests (AC-11.3). "
                            + "Use @Tag(\"integration\") and run with 'mvn verify -P integration'.");
        }

        @Override
        public void checkConnect(String host, int port, Object context) {
            checkConnect(host, port);
        }

        @Override
        public void checkListen(int port) {
            throw new SecurityException(
                    "Unit test attempted to listen on port " + port
                            + " — network I/O is forbidden in unit tests (AC-11.3). "
                            + "Use @Tag(\"integration\") and run with 'mvn verify -P integration'.");
        }

        @Override
        public void checkPermission(Permission perm) {
            // Allow everything except network — handled by checkConnect/checkListen above.
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            // Allow everything except network.
        }
    }
}

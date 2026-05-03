package com.srk.myutils.yd.core;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link InnerTubeClient#create()} factory method to verify
 * NFR-pinned timeout configuration and interceptor wiring.
 *
 * <p>Uses reflection to inspect the OkHttpClient's timeout settings since
 * OkHttp exposes them via public getters. The no-op retry interceptor stub
 * (T-1.6 placeholder) is also verified.
 */
class InnerTubeClientFactoryTest {

    @Nested
    @DisplayName("create() timeout configuration — NFR-NETWORK-TIMEOUT-*")
    class TimeoutConfiguration {

        @Test
        @DisplayName("connect timeout == 10s (NFR-NETWORK-TIMEOUT-CONNECT)")
        void create_connectTimeout() throws Exception {
            OkHttpClient httpClient = extractHttpClient(InnerTubeClient.create());

            assertThat(httpClient.connectTimeoutMillis()).isEqualTo(10_000);
        }

        @Test
        @DisplayName("read timeout == 30s (NFR-NETWORK-TIMEOUT-READ)")
        void create_readTimeout() throws Exception {
            OkHttpClient httpClient = extractHttpClient(InnerTubeClient.create());

            assertThat(httpClient.readTimeoutMillis()).isEqualTo(30_000);
        }

        @Test
        @DisplayName("call timeout == 30s (NFR-INNERTUBE-REQUEST-TIMEOUT)")
        void create_callTimeout() throws Exception {
            OkHttpClient httpClient = extractHttpClient(InnerTubeClient.create());

            assertThat(httpClient.callTimeoutMillis()).isEqualTo(30_000);
        }
    }

    @Nested
    @DisplayName("create() interceptor wiring")
    class InterceptorWiring {

        @Test
        @DisplayName("has exactly one interceptor (no-op retry stub for T-1.6)")
        void create_hasOneInterceptor() throws Exception {
            OkHttpClient httpClient = extractHttpClient(InnerTubeClient.create());

            List<Interceptor> interceptors = httpClient.interceptors();
            assertThat(interceptors).hasSize(1);
        }
    }

    /**
     * Extracts the OkHttpClient from an InnerTubeClient via reflection.
     */
    private static OkHttpClient extractHttpClient(InnerTubeClient innerTubeClient) throws Exception {
        Field field = InnerTubeClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        return (OkHttpClient) field.get(innerTubeClient);
    }
}

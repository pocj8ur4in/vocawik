package com.vocawik.infrastructure.pv.client;

import com.vocawik.service.pv.client.PvApiProperties;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Shared HTTP client and retry policy support for PV metadata clients. */
@Component
@RequiredArgsConstructor
public class PvHttpClientSupport {

    private static final int MAX_BACKOFF_MULTIPLIER_SHIFT = 6;
    private static final long MAX_BACKOFF_DELAY_MS = 5000L;

    private final PvApiProperties pvApiProperties;

    /** Creates a RestClient with provider timeout overrides or shared defaults. */
    public RestClient createRestClient(Integer connectTimeoutMs, Integer readTimeoutMs) {
        PvApiProperties.Http http = pvApiProperties.getHttp();
        int connectTimeout = resolveTimeout(connectTimeoutMs, http.getConnectTimeoutMs());
        int readTimeout = resolveTimeout(readTimeoutMs, http.getReadTimeoutMs());

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    /** Executes request with shared retry policy for retryable HTTP/network failures. */
    public <T> T executeWithRetry(Supplier<T> requestSupplier) {
        int attempts = Math.max(1, pvApiProperties.getHttp().getRetryAttempts());
        int baseDelayMs = Math.max(0, pvApiProperties.getHttp().getRetryDelayMs());

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return requestSupplier.get();
            } catch (RestClientResponseException ex) {
                if (!isRetryableStatus(ex.getStatusCode()) || attempt >= attempts) {
                    throw ex;
                }
                sleepBeforeRetry(baseDelayMs, attempt);
            } catch (RestClientException ex) {
                if (attempt >= attempts) {
                    throw ex;
                }
                sleepBeforeRetry(baseDelayMs, attempt);
            }
        }

        throw new IllegalStateException("unreachable retry state");
    }

    private int resolveTimeout(Integer providerTimeoutMs, int defaultTimeoutMs) {
        int resolved = providerTimeoutMs == null ? defaultTimeoutMs : providerTimeoutMs;
        return Math.max(1, resolved);
    }

    private boolean isRetryableStatus(HttpStatusCode statusCode) {
        int status = statusCode.value();
        return statusCode.is5xxServerError() || status == 408 || status == 429;
    }

    private void sleepBeforeRetry(int baseDelayMs, int attempt) {
        if (baseDelayMs <= 0) {
            return;
        }

        long multiplier = 1L << Math.min(attempt - 1, MAX_BACKOFF_MULTIPLIER_SHIFT);
        long delayMs = Math.min((long) baseDelayMs * multiplier, MAX_BACKOFF_DELAY_MS);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for PV metadata retry", ex);
        }
    }
}

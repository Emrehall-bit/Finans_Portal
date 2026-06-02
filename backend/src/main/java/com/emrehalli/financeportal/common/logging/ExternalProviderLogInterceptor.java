package com.emrehalli.financeportal.common.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpStatusCodeException;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

public class ExternalProviderLogInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ExternalProviderLogInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        long startedAt = System.nanoTime();
        String provider = resolveProvider(request.getURI());
        String endpoint = SensitiveDataMasker.maskUri(request.getURI().toString());

        try {
            ClientHttpResponse response = execution.execute(request, body);
            int statusCode = response.getStatusCode().value();
            logProviderCall(
                    provider,
                    endpoint,
                    elapsedMs(startedAt),
                    !response.getStatusCode().isError(),
                    statusCode,
                    0,
                    null
            );
            return response;
        } catch (IOException | RuntimeException exception) {
            Integer statusCode = exception instanceof HttpStatusCodeException statusException
                    ? statusException.getStatusCode().value()
                    : null;
            logProviderCall(provider, endpoint, elapsedMs(startedAt), false, statusCode, 0, exception);
            throw exception;
        }
    }

    private void logProviderCall(String provider,
                                 String endpoint,
                                 long responseTimeMs,
                                 boolean success,
                                 Integer statusCode,
                                 int retryCount,
                                 Exception exception) {
        String errorMessage = exception == null ? null : SensitiveDataMasker.truncate(exception.getMessage(), 500);
        try (StructuredLogContext ignored = StructuredLogContext.open()
                .put(LoggingConstants.PROVIDER_KEY, provider)
                .put(LoggingConstants.ENDPOINT_KEY, endpoint)
                .put(LoggingConstants.RESPONSE_TIME_MS_KEY, responseTimeMs)
                .put(LoggingConstants.SUCCESS_KEY, success)
                .put(LoggingConstants.STATUS_CODE_KEY, statusCode)
                .put(LoggingConstants.RETRY_COUNT_KEY, retryCount)
                .put("errorMessage", errorMessage)) {
            if (success) {
                logger.info("External provider call completed");
            } else if (logger.isDebugEnabled() && exception != null) {
                logger.warn("External provider call failed. provider={}, statusCode={}, endpoint={}, errorMessage={}",
                        provider, statusCode, endpoint, errorMessage);
                logger.debug("External provider call failed with stacktrace", exception);
            } else {
                logger.warn("External provider call failed. provider={}, statusCode={}, endpoint={}, errorMessage={}",
                        provider, statusCode, endpoint, errorMessage);
            }
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String resolveProvider(URI uri) {
        String host = uri == null || uri.getHost() == null ? "UNKNOWN" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.contains("binance")) return "BINANCE";
        if (host.contains("tefas")) return "TEFAS";
        if (host.contains("tcmb")) return "TCMB";
        if (host.contains("kap.org")) return "KAP";
        if (host.contains("news.google")) return "GOOGLE_NEWS";
        if (host.contains("reuters")) return "REUTERS";
        if (host.contains("borsaistanbul")) return "BIST";
        if (host.contains("yahoo")) return "YAHOO";
        return host.toUpperCase(Locale.ROOT);
    }
}





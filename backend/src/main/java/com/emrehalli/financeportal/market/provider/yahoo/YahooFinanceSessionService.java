package com.emrehalli.financeportal.market.provider.yahoo;

import com.emrehalli.financeportal.market.exception.DataProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class YahooFinanceSessionService {

    private static final String SESSION_BOOTSTRAP_URL = "https://finance.yahoo.com/quote/AAPL";
    private static final String CRUMB_URL = "https://query2.finance.yahoo.com/v1/test/getcrumb";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final org.springframework.web.client.RestTemplate restTemplate;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private final Map<String, String> cookies = new ConcurrentHashMap<>();
    private volatile YahooSession cachedSession;

    public ResponseEntity<String> exchangeWithSession(String urlWithoutCrumb) {
        YahooSession session = ensureSession();

        try {
            return executeYahooGet(urlWithoutCrumb, session.crumb());
        } catch (HttpStatusCodeException exception) {
            if (isRateLimited(exception)) {
                log.warn("Yahoo Finance rate limit returned");
                throw new DataProviderException("Yahoo Finance rate limit returned HTTP 429", exception);
            }
            if (isSessionRejected(exception)) {
                invalidateSession();
                log.warn("Yahoo Finance session rejected; refreshing session and retrying once");
                YahooSession refreshedSession = ensureSession();
                try {
                    return executeYahooGet(urlWithoutCrumb, refreshedSession.crumb());
                } catch (HttpStatusCodeException retryException) {
                    if (isRateLimited(retryException)) {
                        log.warn("Yahoo Finance rate limit returned");
                        throw new DataProviderException("Yahoo Finance rate limit returned HTTP 429", retryException);
                    }
                    throw new DataProviderException(
                            "Yahoo Finance HTTP error after session refresh: " + retryException.getStatusCode().value(),
                            retryException
                    );
                }
            }
            throw exception;
        }
    }

    public void invalidateSession() {
        refreshLock.lock();
        try {
            cookies.clear();
            cachedSession = null;
        } finally {
            refreshLock.unlock();
        }
    }

    private YahooSession ensureSession() {
        YahooSession session = cachedSession;
        if (session != null && hasText(session.crumb()) && !cookies.isEmpty()) {
            return session;
        }

        refreshLock.lock();
        try {
            session = cachedSession;
            if (session != null && hasText(session.crumb()) && !cookies.isEmpty()) {
                return session;
            }

            cookies.clear();
            bootstrapCookies();
            String crumb = fetchCrumb();
            cachedSession = new YahooSession(crumb);
            log.info("Yahoo Finance session created");
            log.info("Yahoo Finance crumb refreshed");
            return cachedSession;
        } finally {
            refreshLock.unlock();
        }
    }

    private void bootstrapCookies() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    SESSION_BOOTSTRAP_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(browserHeaders("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")),
                    String.class
            );
            storeCookies(response.getHeaders());
            if (cookies.isEmpty()) {
                throw new DataProviderException("Yahoo Finance did not return session cookies");
            }
        } catch (HttpStatusCodeException exception) {
            if (isRateLimited(exception)) {
                log.warn("Yahoo Finance rate limit returned");
                throw new DataProviderException("Yahoo Finance rate limit returned HTTP 429", exception);
            }
            throw new DataProviderException(
                    "Yahoo Finance session bootstrap failed: HTTP " + exception.getStatusCode().value(),
                    exception
            );
        }
    }

    private String fetchCrumb() {
        try {
            HttpHeaders headers = browserHeaders("text/plain,application/json,*/*;q=0.8");
            addCookieHeader(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    CRUMB_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            storeCookies(response.getHeaders());

            String crumb = response.getBody() == null ? "" : response.getBody().trim();
            if (!isValidCrumb(crumb, response.getHeaders())) {
                throw new DataProviderException("Yahoo Finance returned an invalid crumb response");
            }
            return crumb;
        } catch (HttpStatusCodeException exception) {
            if (isRateLimited(exception)) {
                log.warn("Yahoo Finance rate limit returned");
                throw new DataProviderException("Yahoo Finance rate limit returned HTTP 429", exception);
            }
            throw new DataProviderException(
                    "Yahoo Finance crumb refresh failed: HTTP " + exception.getStatusCode().value(),
                    exception
            );
        }
    }

    private ResponseEntity<String> executeYahooGet(String urlWithoutCrumb, String crumb) {
        HttpHeaders headers = browserHeaders("application/json,text/plain,*/*");
        addCookieHeader(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                appendCrumb(urlWithoutCrumb, crumb),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
        storeCookies(response.getHeaders());
        return response;
    }

    private HttpHeaders browserHeaders(String accept) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT, accept);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9,tr;q=0.8");
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache");
        return headers;
    }

    private void addCookieHeader(HttpHeaders headers) {
        String cookieHeader = cookieHeader();
        if (hasText(cookieHeader)) {
            headers.set(HttpHeaders.COOKIE, cookieHeader);
        }
    }

    private void storeCookies(HttpHeaders headers) {
        List<String> setCookieHeaders = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return;
        }

        for (String setCookie : setCookieHeaders) {
            if (!hasText(setCookie)) {
                continue;
            }
            String firstPart = setCookie.split(";", 2)[0];
            int separator = firstPart.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = firstPart.substring(0, separator).trim();
            String value = firstPart.substring(separator + 1).trim();
            if (!hasText(name)) {
                continue;
            }
            if (value.isEmpty()) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }

    private String cookieHeader() {
        return cookies.entrySet().stream()
                .filter(entry -> hasText(entry.getKey()) && hasText(entry.getValue()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    private String appendCrumb(String url, String crumb) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "crumb=" + URLEncoder.encode(crumb, StandardCharsets.UTF_8);
    }

    private boolean isValidCrumb(String crumb, HttpHeaders headers) {
        if (!hasText(crumb) || crumb.length() > 256) {
            return false;
        }

        String contentType = headers.getContentType() == null ? "" : headers.getContentType().toString().toLowerCase();
        String normalized = crumb.toLowerCase();

        if (contentType.contains("html")) {
            return false;
        }
        if (normalized.contains("<html") || normalized.contains("<!doctype") || normalized.contains("too many requests")) {
            return false;
        }
        if (crumb.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        return crumb.matches("^[^<>{}\\[\\]]+$");
    }

    private boolean isSessionRejected(HttpStatusCodeException exception) {
        int status = exception.getStatusCode().value();
        return status == 401 || status == 403;
    }

    private boolean isRateLimited(HttpStatusCodeException exception) {
        return exception.getStatusCode().value() == 429;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record YahooSession(String crumb) {
    }
}

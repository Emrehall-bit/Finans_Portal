package com.emrehalli.financeportal.market.provider.fund;

import com.emrehalli.financeportal.common.logging.LoggingConstants;
import com.emrehalli.financeportal.common.logging.StructuredLogContext;
import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.fund.dto.FundNavDto;
import com.emrehalli.financeportal.market.provider.fund.dto.TefasFundListRequest;
import com.emrehalli.financeportal.market.provider.fund.dto.TefasFundListResponse;
import com.emrehalli.financeportal.market.provider.fund.dto.TefasFundListResponseItem;
import com.emrehalli.financeportal.market.provider.fund.dto.TefasFundPriceRequest;
import com.emrehalli.financeportal.market.provider.fund.dto.TefasFundPriceResponse;
import com.emrehalli.financeportal.market.provider.fund.dto.TefasFundPriceResponseItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TEFAS fund market data provider.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TefasProvider implements MarketDataProvider {

    private static final String LIST_ENDPOINT = "https://www.tefas.gov.tr/api/funds/fonGetiriBazliBilgiGetir";
    private static final String PRICE_ENDPOINT = "https://www.tefas.gov.tr/api/funds/fonFiyatBilgiGetir";
    private static final String SESSION_ENDPOINT = "https://www.tefas.gov.tr/TefasGrafik.aspx";
    private static final int DEFAULT_HISTORY_PERIOD = 1;

    private final MarketProperties props;
    private final ObjectMapper objectMapper;

    private final CookieStore cookieStore = new BasicCookieStore();
    private final CloseableHttpClient httpClient = HttpClients.custom()
            .setDefaultCookieStore(cookieStore)
            .build();
    private final AtomicBoolean sessionInitialized = new AtomicBoolean(false);

    @Override
    public String getSourceName() {
        return SourceName.TEFAS.name();
    }

    @Override
    public List<FundNavDto> fetch() {
        try {
            List<FundNavDto> funds = fetchFromApi();
            if (funds.isEmpty()) {
                log.warn("TEFAS provider returned no fund NAV data.");
            }
            return funds;
        } catch (DataProviderException exception) {
            log.error("Failed to fetch fund NAV data from TEFAS", exception);
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to fetch fund NAV data from TEFAS", exception);
            throw new DataProviderException("Failed to fetch fund NAV data from TEFAS", exception);
        }
    }

    private List<FundNavDto> fetchFromApi() {
        List<TefasFundListResponseItem> funds = fetchAllFunds();
        if (funds.isEmpty()) {
            return Collections.emptyList();
        }

        List<FundNavDto> result = new ArrayList<>();
        for (TefasFundListResponseItem item : funds) {
            if (item == null || item.getFonKodu() == null || item.getFonKodu().isBlank()) {
                continue;
            }

            List<TefasFundPriceResponseItem> history = fetchFundHistory(item.getFonKodu(), DEFAULT_HISTORY_PERIOD);
            TefasFundPriceResponseItem latestPrice = history.stream()
                    .filter(Objects::nonNull)
                    .filter(price -> price.getTarih() != null && !price.getTarih().isBlank())
                    .filter(price -> price.getFiyat() != null)
                    .max(Comparator.comparing(price -> LocalDate.parse(price.getTarih())))
                    .orElse(null);

            if (latestPrice == null) {
                log.warn("Skipping TEFAS fund because latest price could not be resolved. fonKodu={}", item.getFonKodu());
                continue;
            }

            result.add(FundNavDto.builder()
                    .fundCode(item.getFonKodu())
                    .fundName(item.getFonUnvan())
                    .navValue(latestPrice.getFiyat())
                    .getiri1a(item.getGetiri1a())
                    .getiri3a(item.getGetiri3a())
                    .getiri6a(item.getGetiri6a())
                    .getiriYb(item.getGetiriYb())
                    .getiri1y(item.getGetiri1y())
                    .getiri3y(item.getGetiri3y())
                    .getiri5y(item.getGetiri5y())
                    .riskDegeri(item.getRiskDegeri())
                    .fonTurAciklama(item.getFonTurAciklama())
                    .navDate(LocalDate.parse(latestPrice.getTarih()))
                    .fundType(item.getFonTurAciklama())
                    .build());
        }

        return result;
    }

    public List<TefasFundListResponseItem> fetchAllFunds() {
        long startedAt = System.nanoTime();
        try {
            initSession();
            TefasFundListRequest requestPayload = new TefasFundListRequest();
            String requestBody = objectMapper.writeValueAsString(requestPayload);
            HttpPost request = new HttpPost(LIST_ENDPOINT);
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            request.setHeader("Referer", "https://www.tefas.gov.tr/TefasGrafik.aspx");
            request.setHeader("Origin", "https://www.tefas.gov.tr");
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Accept", "application/json, text/plain, */*");
            log.info("TEFAS request body: {}", requestBody);
            log.info("TEFAS Content-Type header: {}", request.getFirstHeader("Content-Type"));
            request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

            TefasFundListResponse response = httpClient.execute(request, httpResponse -> {
                HttpEntity entity = httpResponse.getEntity();
                if (entity == null) {
                    return null;
                }

                String rawBody = EntityUtils.toString(entity);
                log.info("TEFAS raw response: {}", rawBody);
                if (rawBody == null || rawBody.isBlank()) {
                    return null;
                }

                return objectMapper.readValue(rawBody, TefasFundListResponse.class);
            });

            if (response == null) {
                log.error("TEFAS fund list response is null.");
                logProviderCall(LIST_ENDPOINT, startedAt, false, null);
                return Collections.emptyList();
            }

            if (response.getErrorCode() != null) {
                log.error("TEFAS fund list request failed. errorCode={}, errorMessage={}",
                        response.getErrorCode(), response.getErrorMessage());
                logProviderCall(LIST_ENDPOINT, startedAt, false, null);
                return Collections.emptyList();
            }

            List<TefasFundListResponseItem> resultList = response.getResultList() == null
                    ? Collections.emptyList()
                    : response.getResultList();
            log.info("TEFAS resultList size: {}", resultList.size());
            if (!resultList.isEmpty()) {
                log.info("TEFAS first item: {}", resultList.get(0));
            }

            List<TefasFundListResponseItem> result = resultList.stream()
                    .filter(Objects::nonNull)
                    .filter(item -> Boolean.TRUE.equals(item.getTefasDurum()))
                    .toList();
            log.info("TEFAS fund list fetched, count: {}", result.size());
            logProviderCall(LIST_ENDPOINT, startedAt, true, null);
            return result;
        } catch (Exception exception) {
            log.error("Failed to fetch TEFAS fund list.", exception);
            logProviderCall(LIST_ENDPOINT, startedAt, false, exception);
            throw new DataProviderException("Failed to fetch TEFAS fund list", exception);
        }
    }

    public List<TefasFundPriceResponseItem> fetchFundHistory(String fonKodu, int periyod) {
        long startedAt = System.nanoTime();
        try {
            initSession();
            TefasFundPriceRequest requestPayload = new TefasFundPriceRequest(fonKodu, "TR", periyod);
            String requestBody = objectMapper.writeValueAsString(requestPayload);
            HttpPost request = new HttpPost(PRICE_ENDPOINT);
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            request.setHeader("Referer", "https://www.tefas.gov.tr/TefasGrafik.aspx");
            request.setHeader("Origin", "https://www.tefas.gov.tr");
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Accept", "application/json, text/plain, */*");
            request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
            log.info("fetchFundHistory request: fonKodu={}, periyod={}", fonKodu, periyod);

            TefasFundPriceResponse response = httpClient.execute(request, httpResponse -> {
                HttpEntity entity = httpResponse.getEntity();
                if (entity == null) {
                    return null;
                }

                String rawResponse = EntityUtils.toString(entity);
                log.info("fetchFundHistory raw response: {}", rawResponse);
                if (rawResponse == null || rawResponse.isBlank()) {
                    return null;
                }

                return objectMapper.readValue(rawResponse, TefasFundPriceResponse.class);
            });

            if (response == null) {
                log.error("TEFAS fund price response is null. fonKodu={}, periyod={}", fonKodu, periyod);
                logProviderCall(PRICE_ENDPOINT, startedAt, false, null);
                return Collections.emptyList();
            }

            if (response.getErrorCode() != null) {
                log.error("TEFAS fund price request failed. fonKodu={}, periyod={}, errorCode={}, errorMessage={}",
                        fonKodu, periyod, response.getErrorCode(), response.getErrorMessage());
                logProviderCall(PRICE_ENDPOINT, startedAt, false, null);
                return Collections.emptyList();
            }

            List<TefasFundPriceResponseItem> result = response.getResultList() == null ? Collections.emptyList() : response.getResultList();
            logProviderCall(PRICE_ENDPOINT, startedAt, true, null);
            return result;
        } catch (Exception exception) {
            log.error("Failed to fetch TEFAS fund history. fonKodu={}, periyod={}", fonKodu, periyod, exception);
            logProviderCall(PRICE_ENDPOINT, startedAt, false, exception);
            return Collections.emptyList();
        }
    }

    private void initSession() {
        if (sessionInitialized.get()) {
            return;
        }

        synchronized (sessionInitialized) {
            if (sessionInitialized.get()) {
                return;
            }

            try {
                HttpGet request = new HttpGet(SESSION_ENDPOINT);
                request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                request.setHeader("Referer", "https://www.tefas.gov.tr/TefasGrafik.aspx");
                request.setHeader("Origin", "https://www.tefas.gov.tr");
                request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

                httpClient.execute(request, response -> {
                    EntityUtils.consumeQuietly(response.getEntity());
                    return null;
                });

                sessionInitialized.set(true);
            } catch (Exception exception) {
                throw new DataProviderException("Failed to initialize TEFAS session", exception);
            }
        }
    }

    private <T> T executePost(String url, Object body, Class<T> responseType) {
        try {
            HttpPost request = new HttpPost(url);
            request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            request.setHeader("Referer", "https://www.tefas.gov.tr/TefasGrafik.aspx");
            request.setHeader("Origin", "https://www.tefas.gov.tr");
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Accept", "application/json, text/plain, */*");
            request.setEntity(new StringEntity(objectMapper.writeValueAsString(body), ContentType.APPLICATION_JSON));

            return httpClient.execute(request, response -> {
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    return null;
                }

                String responseBody = EntityUtils.toString(entity);
                if (responseBody == null || responseBody.isBlank()) {
                    return null;
                }

                return objectMapper.readValue(responseBody, responseType);
            });
        } catch (Exception exception) {
            throw new DataProviderException("Failed TEFAS POST request to " + url, exception);
        }
    }

    private void logProviderCall(String endpoint, long startedAt, boolean success, Exception exception) {
        long responseTimeMs = (System.nanoTime() - startedAt) / 1_000_000;
        try (StructuredLogContext ignored = StructuredLogContext.open()
                .put(LoggingConstants.PROVIDER_KEY, "TEFAS")
                .put(LoggingConstants.ENDPOINT_KEY, endpoint)
                .put(LoggingConstants.RESPONSE_TIME_MS_KEY, responseTimeMs)
                .put(LoggingConstants.SUCCESS_KEY, success)
                .put(LoggingConstants.RETRY_COUNT_KEY, 0)) {
            if (success) {
                log.info("External provider call completed");
            } else if (exception == null) {
                log.error("External provider call failed");
            } else {
                log.error("External provider call failed", exception);
            }
        }
    }
}





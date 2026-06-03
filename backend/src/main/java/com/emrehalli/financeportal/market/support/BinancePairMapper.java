package com.emrehalli.financeportal.market.support;

import com.emrehalli.financeportal.config.MarketProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Maps whitelisted Binance TRY trading pairs.
 * Only base assets present in {@link CryptoWhitelist} are accepted.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BinancePairMapper {

    private static final List<String> FALLBACK_BASE_ASSETS =
            List.of("BTC", "ETH", "BNB", "SOL", "XRP", "USDT", "DOGE", "ADA", "AVAX", "DOT");

    private final MarketProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CryptoWhitelist cryptoWhitelist;

    private final Map<String, String> symbolMap = new ConcurrentHashMap<>();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        boolean loaded = tryLoadFromBinance();
        if (!loaded) {
            log.warn("[BinancePairMapper] Binance TRY pariteleri init sirasinda yuklenemedi. Fallback uygulanacak.");
            applyFallback();
        }
    }

    // Binance parite delist/relist senaryolarına karşı periyodik yenileme.
    // Whitelist statik olsa da Binance tarafındaki değişiklikler batch ticker isteğini bozabilir.
    @Scheduled(initialDelay = 4 * 60 * 60 * 1000, fixedRate = 4 * 60 * 60 * 1000)
    public void scheduledReload() {
        if (loading.get()) {
            return;
        }
        boolean loaded = tryLoadFromBinance();
        if (!loaded && symbolMap.isEmpty()) {
            applyFallback();
        }
    }

    public List<String> getAllSymbols() {
        return new ArrayList<>(symbolMap.values());
    }

    public String toDisplayCode(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }

        String normalized = symbol.trim().toUpperCase();
        for (Map.Entry<String, String> entry : symbolMap.entrySet()) {
            if (entry.getValue().equals(normalized) || entry.getKey().equals(normalized)) {
                return entry.getKey();
            }
        }

        if (normalized.endsWith("TRY") && normalized.length() > 3) {
            return normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    public boolean isSupported(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }

        String normalized = symbol.trim().toUpperCase();
        return symbolMap.containsKey(normalized) || symbolMap.containsValue(normalized);
    }

    public boolean isReady() {
        return !symbolMap.isEmpty();
    }

    private boolean tryLoadFromBinance() {
        if (!loading.compareAndSet(false, true)) {
            return false;
        }

        try {
            String baseUrl = props.getProviders().getBinance().getBaseUrl();
            String url = baseUrl + "/ticker/price";
            String response = restTemplate.getForObject(url, String.class);

            if (response == null || response.isBlank()) {
                log.warn("[BinancePairMapper] Binance pair load empty payload dondu.");
                return false;
            }

            JsonNode root = objectMapper.readTree(response);
            if (!root.isArray()) {
                log.warn("[BinancePairMapper] Binance pair load beklenmeyen payload tipi dondu.");
                return false;
            }

            Map<String, String> discoveredPairs = new ConcurrentHashMap<>();
            for (JsonNode item : root) {
                String symbol = item.path("symbol").asText("");
                if (!isTryPair(symbol)) {
                    continue;
                }

                String baseAsset = symbol.substring(0, symbol.length() - 3);
                if (baseAsset.isBlank() || !cryptoWhitelist.isWhitelisted(baseAsset)) {
                    continue;
                }
                discoveredPairs.put(baseAsset, symbol);
            }

            if (discoveredPairs.isEmpty()) {
                log.warn("[BinancePairMapper] Binance yanitinda whitelist ile eslesen TRY pariti bulunamadi.");
                return false;
            }

            symbolMap.clear();
            symbolMap.putAll(discoveredPairs);
            log.info("[BinancePairMapper] {} adet whitelist TRY pariti yuklendi.", symbolMap.size());
            return true;
        } catch (Exception exception) {
            log.warn("[BinancePairMapper] Binance TRY pariteleri yuklenemedi.", exception);
            return false;
        } finally {
            loading.set(false);
        }
    }

    private void applyFallback() {
        symbolMap.clear();
        FALLBACK_BASE_ASSETS.forEach(base -> symbolMap.put(base, base + "TRY"));
        log.info("[BinancePairMapper] Fallback semboller uygulandı: {}", symbolMap.keySet());
    }

    private boolean isTryPair(String symbol) {
        return symbol != null && symbol.endsWith("TRY");
    }
}

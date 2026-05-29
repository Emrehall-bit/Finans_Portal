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
 * Mapper for Binance TRY trading pairs.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BinancePairMapper {

    private static final Map<String, String> FALLBACK_SYMBOLS = Map.of(
            "BTC", "BTCTRY",
            "ETH", "ETHTRY",
            "BNB", "BNBTRY",
            "SOL", "SOLTRY",
            "XRP", "XRPTRY"
    );

    private final MarketProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void scheduledReload() {
        if (loading.get()) {
            log.debug("[BinancePairMapper] YÃ¼kleme devam ediyor, atlanÄ±yor.");
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
            log.debug("[BinancePairMapper] YÃ¼kleme devam ediyor, atlanÄ±yor.");
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

                String displayCode = symbol.substring(0, symbol.length() - 3);
                if (displayCode.isBlank()) {
                    continue;
                }
                discoveredPairs.put(displayCode, symbol);
            }

            if (discoveredPairs.isEmpty()) {
                log.warn("[BinancePairMapper] Binance yanÄ±tÄ± geldi ama hiÃ§ TRY pariti bulunamadÄ±.");
                return false;
            }

            symbolMap.clear();
            symbolMap.putAll(discoveredPairs);
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
        symbolMap.putAll(FALLBACK_SYMBOLS);
        log.info("[BinancePairMapper] Fallback semboller uygulandÄ±: {}", FALLBACK_SYMBOLS.keySet());
    }

    private boolean isTryPair(String symbol) {
        return symbol != null
                && symbol.endsWith("TRY")
                && !symbol.endsWith("USTRY");
    }
}





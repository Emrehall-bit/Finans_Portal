package com.emrehalli.financeportal.market.support;

import com.emrehalli.financeportal.config.MarketProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper for Binance TRY trading pairs.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BinancePairMapper {

    private final MarketProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, String> symbolMap = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        try {
            String baseUrl = props.getProviders().getBinance().getBaseUrl();
            String url = baseUrl + "/ticker/price";
            String response = restTemplate.getForObject(url, String.class);

            if (response == null || response.isBlank()) {
                log.error("Binance pair mapper initialization returned empty payload");
                return;
            }

            JsonNode root = objectMapper.readTree(response);
            if (!root.isArray()) {
                log.error("Binance pair mapper initialization returned unexpected payload type");
                return;
            }

            Map<String, String> discoveredPairs = new LinkedHashMap<>();
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

            symbolMap.clear();
            symbolMap.putAll(discoveredPairs);
        } catch (Exception exception) {
            log.error("Failed to initialize Binance TRY pair mapper", exception);
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

    private boolean isTryPair(String symbol) {
        return symbol != null
                && symbol.endsWith("TRY")
                && !symbol.endsWith("USTRY");
    }
}

package com.emrehalli.financeportal.market.provider.bist.client;

import com.emrehalli.financeportal.config.BistProperties;
import com.emrehalli.financeportal.market.provider.bist.dto.BistViopResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BistViopClient {

    private static final Logger logger = LogManager.getLogger(BistViopClient.class);

    private final BistProperties bistProperties;

    public BistViopClient(BistProperties bistProperties) {
        this.bistProperties = bistProperties;
    }

    public BistViopResponse fetchSnapshotData() {
        if (!bistProperties.isEnabled()) {
            logger.debug("BIST/VIOP snapshot fetch skipped because provider is disabled.");
            return emptyResponse();
        }

        if (bistProperties.getBaseUrl() == null || bistProperties.getBaseUrl().isBlank()) {
            logger.warn("BIST/VIOP provider is enabled but base URL is missing. Snapshot fetch skipped.");
            return emptyResponse();
        }

        if (bistProperties.getContracts() == null || bistProperties.getContracts().isEmpty()) {
            logger.warn("BIST/VIOP provider is enabled but no contract whitelist is configured. Snapshot fetch skipped.");
            return emptyResponse();
        }

        logger.info("BIST/VIOP reference fetch started for {} contracts against {}", bistProperties.getContracts().size(),
                bistProperties.getBaseUrl());

        LocalDateTime fetchedAt = LocalDateTime.now();
        List<Map<String, Object>> items = new ArrayList<>();

        for (BistProperties.Contract contract : bistProperties.getContracts()) {
            if (contract.getPath() == null || contract.getPath().isBlank()) {
                logger.debug("BIST/VIOP contract {} skipped because path is missing.", contract.getSymbol());
                continue;
            }

            String endpoint = buildEndpoint(contract.getPath());

            try {
                Document document = Jsoup.connect(endpoint)
                        .userAgent("Mozilla/5.0")
                        .timeout(resolveTimeout())
                        .get();

                String title = document.select("h1").stream()
                        .map(element -> element.text().trim())
                        .filter(text -> !text.isBlank())
                        .findFirst()
                        .orElse(contract.getName());

                String summary = document.select("main p, article p, .content p").stream()
                        .map(element -> element.text().trim())
                        .filter(text -> !text.isBlank())
                        .findFirst()
                        .orElse(null);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("symbol", contract.getSymbol());
                item.put("name", title != null && !title.isBlank() ? title : contract.getName());
                item.put("currency", contract.getCurrency());
                item.put("url", endpoint);
                item.put("summary", summary);
                items.add(item);
            } catch (IOException e) {
                logger.warn("BIST/VIOP reference fetch failed for contract {} via {}", contract.getSymbol(), endpoint, e);
            }
        }

        logger.info("BIST/VIOP reference fetch completed with {} raw items", items.size());
        return BistViopResponse.builder()
                .fetchedAt(fetchedAt)
                .items(items)
                .build();
    }

    public BistViopResponse fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        logger.info("BIST/VIOP historical fetch requested for {} between {} and {}", symbol, startDate, endDate);

        logger.info("BIST/VIOP historical integration is not implemented yet. Returning empty response for {}", symbol);
        return emptyResponse();
    }

    private int resolveTimeout() {
        return bistProperties.getHttp().getReadTimeoutMs() != null
                ? bistProperties.getHttp().getReadTimeoutMs()
                : 5000;
    }

    private String buildEndpoint(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }

        return bistProperties.getBaseUrl() + path;
    }

    private BistViopResponse emptyResponse() {
        return BistViopResponse.builder()
                .fetchedAt(LocalDateTime.now())
                .items(List.of())
                .build();
    }
}

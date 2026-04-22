package com.emrehalli.financeportal.market.provider.tefas.client;

import com.emrehalli.financeportal.config.TefasProperties;
import com.emrehalli.financeportal.market.provider.tefas.dto.TefasFundResponse;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TefasClient {

    private static final Logger logger = LogManager.getLogger(TefasClient.class);
    private static final Pattern LAST_PRICE_PATTERN = Pattern.compile("Son Fiyat \\(TL\\)\\s*([\\d.,]+)");
    private static final Pattern DAILY_RETURN_PATTERN = Pattern.compile("Günlük Getiri \\(%\\)\\s*%?([-\\d.,]+)");

    private final TefasProperties tefasProperties;

    public TefasClient(TefasProperties tefasProperties) {
        this.tefasProperties = tefasProperties;
    }

    public TefasFundResponse fetchSnapshotData() {
        if (!tefasProperties.isEnabled()) {
            logger.debug("TEFAS snapshot fetch skipped because provider is disabled.");
            return emptyResponse();
        }

        if (tefasProperties.getBaseUrl() == null || tefasProperties.getBaseUrl().isBlank()) {
            logger.warn("TEFAS provider is enabled but base URL is missing. Snapshot fetch skipped.");
            return emptyResponse();
        }

        List<String> funds = tefasProperties.getFunds().stream()
                .filter(fund -> fund != null && !fund.isBlank())
                .map(String::trim)
                .toList();

        if (funds.isEmpty()) {
            logger.warn("TEFAS provider is enabled but no fund whitelist is configured. Snapshot fetch skipped.");
            return emptyResponse();
        }

        logger.info("TEFAS snapshot fetch started for {} funds against {}", funds.size(), tefasProperties.getBaseUrl());
        LocalDateTime fetchedAt = LocalDateTime.now();
        List<Map<String, Object>> items = new ArrayList<>();

        for (String fund : funds) {
            String endpoint = tefasProperties.getBaseUrl() + "/FonAnaliz.aspx?FonKod=" + fund;

            try {
                Document document = Jsoup.connect(endpoint)
                        .userAgent("Mozilla/5.0")
                        .timeout(resolveTimeout())
                        .get();

                String pageText = document.text();
                String name = document.select("h2").stream()
                        .map(element -> element.text().trim())
                        .filter(text -> !text.isBlank())
                        .findFirst()
                        .orElse(fund);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("symbol", fund);
                item.put("name", name);
                item.put("price", extract(pageText, LAST_PRICE_PATTERN));
                item.put("changePercent", extract(pageText, DAILY_RETURN_PATTERN));
                item.put("url", endpoint);
                items.add(item);
            } catch (IOException e) {
                logger.warn("TEFAS snapshot fetch failed for fund {} via {}", fund, endpoint, e);
            }
        }

        logger.info("TEFAS snapshot fetch completed with {} raw fund items", items.size());
        return TefasFundResponse.builder()
                .fetchedAt(fetchedAt)
                .items(items)
                .build();
    }

    public TefasFundResponse fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        if (!tefasProperties.isEnabled()) {
            logger.debug("TEFAS historical fetch skipped because provider is disabled.");
            return emptyResponse();
        }

        if (tefasProperties.getBaseUrl() == null || tefasProperties.getBaseUrl().isBlank()) {
            logger.warn("TEFAS provider is enabled but base URL is missing. Historical fetch skipped for {}.", symbol);
            return emptyResponse();
        }

        logger.info("TEFAS historical fetch requested for {} between {} and {}", symbol, startDate, endDate);
        logger.info("TEFAS historical integration is not implemented yet. Returning empty response for {}", symbol);
        return emptyResponse();
    }

    private int resolveTimeout() {
        return tefasProperties.getHttp().getReadTimeoutMs() != null
                ? tefasProperties.getHttp().getReadTimeoutMs()
                : 5000;
    }

    private String extract(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private TefasFundResponse emptyResponse() {
        return TefasFundResponse.builder()
                .fetchedAt(LocalDateTime.now())
                .items(List.of())
                .build();
    }
}

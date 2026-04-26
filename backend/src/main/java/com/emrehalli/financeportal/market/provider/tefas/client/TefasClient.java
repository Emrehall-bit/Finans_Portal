package com.emrehalli.financeportal.market.provider.tefas.client;

import com.emrehalli.financeportal.market.provider.tefas.config.TefasProviderProperties;
import com.emrehalli.financeportal.market.provider.tefas.dto.TefasFundResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TefasClient {

    private static final Logger log = LoggerFactory.getLogger(TefasClient.class);
    private static final Pattern LAST_PRICE_PATTERN = Pattern.compile(
            "son\\s*fiyat(?:\\s*\\(tl\\))?[^\\d-]{0,20}([-]?[\\d.,]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern DAILY_RETURN_PATTERN = Pattern.compile(
            "g[üu]nl[üu]k\\s*getiri(?:\\s*\\(%\\))?[^\\d%-]{0,20}%?\\s*([-]?[\\d.,]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PRICE_DATE_PATTERN = Pattern.compile(
            "fiyat\\s*tarihi[^\\d]{0,20}(\\d{2}[./-]\\d{2}[./-]\\d{4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private final RestTemplate restTemplate;
    private final TefasProviderProperties properties;

    public TefasClient(RestTemplate restTemplate,
                       TefasProviderProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public List<TefasFundResponse> fetchFunds(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            log.info("TEFAS client fetch skipped: empty symbol list");
            return List.of();
        }

        return symbols.stream()
                .flatMap(symbol -> fetchFund(symbol).stream())
                .toList();
    }

    private Optional<TefasFundResponse> fetchFund(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }

        URI uri = buildFundUri(symbol.trim());
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            String html = response.getBody();
            if (html == null || html.isBlank()) {
                log.info("TEFAS client request completed: symbol={}, empty response body", symbol);
                return Optional.empty();
            }

            return parseFundResponse(symbol.trim(), uri, html);
        } catch (Exception ex) {
            log.warn("TEFAS client request failed: symbol={}, uri={}, error={}", symbol, uri, ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    URI buildFundUri(String symbol) {
        return UriComponentsBuilder.fromHttpUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .path("/FonAnaliz.aspx")
                .queryParam("FonKod", symbol)
                .build(true)
                .toUri();
    }

    private Optional<TefasFundResponse> parseFundResponse(String symbol, URI uri, String html) {
        Document document = Jsoup.parse(html, uri.toString());
        String pageText = normalizeText(document.text());
        String displayName = document.select("h2").stream()
                .map(element -> element.text().trim())
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse(symbol);

        String price = extract(pageText, LAST_PRICE_PATTERN);
        if (price == null || price.isBlank()) {
            log.info(
                    "TEFAS client parse skipped: symbol={}, reason=missing-price, pageTextSnippet={}",
                    symbol,
                    abbreviate(pageText)
            );
            return Optional.empty();
        }

        return Optional.of(new TefasFundResponse(
                symbol,
                displayName,
                price,
                extract(pageText, DAILY_RETURN_PATTERN),
                parseDate(extract(pageText, PRICE_DATE_PATTERN)).orElse(LocalDate.now(ZoneOffset.UTC))
        ));
    }

    private Optional<LocalDate> parseDate(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.replace('.', '-').replace('/', '-');
        try {
            return Optional.of(LocalDate.parse(normalized, DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private String extract(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ');

        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC);
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return value.length() <= 300
                ? value
                : value.substring(0, 300) + "...";
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("TEFAS base URL is not configured");
        }

        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }
}

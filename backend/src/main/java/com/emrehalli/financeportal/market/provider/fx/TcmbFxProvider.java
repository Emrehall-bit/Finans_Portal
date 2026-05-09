package com.emrehalli.financeportal.market.provider.fx;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TCMB FX market data provider.
 */
@Component
@Slf4j
public class TcmbFxProvider extends AbstractFxProvider implements MarketDataProvider {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String END_DATE = "01-01-2999";
    private static final List<String> SERIES_CODES = List.of(
            "TP_DK_EUR_A_YTL", "TP_DK_EUR_S_YTL",
            "TP_DK_USD_A_YTL", "TP_DK_USD_S_YTL",
            "TP_DK_AUD_A_YTL", "TP_DK_AUD_S_YTL",
            "TP_DK_AZN_A_YTL", "TP_DK_AZN_S_YTL",
            "TP_DK_CNY_A_YTL", "TP_DK_CNY_S_YTL",
            "TP_DK_KRW_A_YTL", "TP_DK_KRW_S_YTL",
            "TP_DK_GBP_A_YTL", "TP_DK_GBP_S_YTL",
            "TP_DK_JPY_A_YTL", "TP_DK_JPY_S_YTL",
            "TP_DK_KWD_A_YTL", "TP_DK_KWD_S_YTL",
            "TP_DK_QAR_A_YTL", "TP_DK_QAR_S_YTL",
            "TP_DK_RUB_A_YTL", "TP_DK_RUB_S_YTL",
            "TP_DK_CHF_A_YTL", "TP_DK_CHF_S_YTL",
            "TP_DK_CAD_A_YTL", "TP_DK_CAD_S_YTL"
    );
    private static final Map<String, PriceType> TYPE_MAP = Map.of(
            "A", PriceType.BUY,
            "S", PriceType.SELL,
            "C", PriceType.SELL
    );

    private final MarketProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TcmbFxProvider(RestTemplate restTemplate, ObjectMapper objectMapper, MarketProperties props) {
        super(restTemplate, objectMapper);
        this.props = props;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSourceName() {
        return SourceName.TCMB.name();
    }

    @Override
    public List<FxRateDto> fetch() {
        return fetchFromApi();
    }

    private List<FxRateDto> fetchFromApi() {
        String startDate = LocalDate.now().minusDays(5).format(DATE_FORMATTER);
        String series = SERIES_CODES.stream()
                .map(code -> code.replace('_', '.'))
                .reduce((left, right) -> left + "-" + right)
                .orElse("");
        String url = props.getProviders().getTcmb().getBaseUrl() + "/igmevdsms-dis/series=" + series
                + "&startDate=" + startDate
                + "&endDate=" + END_DATE
                + "&type=json";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("key", props.getProviders().getTcmb().getApiKey());

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("TCMB provider returned empty payload. Weekend or holiday assumed.");
                return List.of();
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                log.warn("TCMB provider returned empty items list. Weekend or holiday assumed.");
                return List.of();
            }

            List<FxRateDto> rates = new ArrayList<>();
            for (JsonNode item : items) {
                LocalDateTime timestamp = parseItemDate(item.path("Tarih").asText(null));
                if (timestamp == null) {
                    continue;
                }

                for (String seriesCode : SERIES_CODES) {
                    JsonNode valueNode = item.get(seriesCode);
                    if (valueNode == null || valueNode.isNull()) {
                        continue;
                    }

                    String valueText = valueNode.asText();
                    if (valueText == null || valueText.isBlank()) {
                        continue;
                    }

                    BigDecimal priceValue = parseDecimal(valueText);
                    if (priceValue == null) {
                        continue;
                    }

                    ParsedSeries parsedSeries = parseSeries(seriesCode);
                    FxRateDto.FxRateDtoBuilder builder = FxRateDto.builder()
                            .sourceName(SourceName.TCMB)
                            .currencyCode(parsedSeries.currencyCode())
                            .referencePrice(null)
                            .dataTimestamp(timestamp);

                    if (parsedSeries.priceType() == PriceType.BUY) {
                        builder.buyPrice(priceValue).sellPrice(null);
                    } else {
                        builder.sellPrice(priceValue).buyPrice(null);
                    }

                    rates.add(builder.build());
                }
            }

            if (rates.isEmpty()) {
                log.warn("TCMB provider returned no parsable FX records. Weekend or holiday assumed.");
            }
            return rates;
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 403) {
                log.error("TCMB API key gecersiz veya eksik", exception);
                throw new DataProviderException("TCMB API key geçersiz veya eksik", exception);
            }
            log.error("Failed to fetch FX data from TCMB. HTTP status={}", exception.getStatusCode().value(), exception);
            throw new DataProviderException("Failed to fetch FX data from TCMB", exception);
        } catch (DataProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to fetch or parse FX data from TCMB", exception);
            throw new DataProviderException("Failed to fetch FX data from TCMB", exception);
        }
    }

    private LocalDateTime parseItemDate(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateText, DATE_FORMATTER).atStartOfDay();
        } catch (DateTimeParseException exception) {
            log.error("Failed to parse TCMB date {}", dateText, exception);
            throw new DataProviderException("Failed to parse TCMB response date", exception);
        }
    }

    private BigDecimal parseDecimal(String valueText) {
        try {
            return new BigDecimal(valueText.trim().replace(",", "."));
        } catch (NumberFormatException exception) {
            log.error("Failed to parse TCMB FX value {}", valueText, exception);
            throw new DataProviderException("Failed to parse TCMB response value", exception);
        }
    }

    private ParsedSeries parseSeries(String seriesCode) {
        String[] parts = seriesCode.split("_");
        String currencyCode = parts[2];
        PriceType priceType = TYPE_MAP.getOrDefault(parts[3], PriceType.SELL);
        return new ParsedSeries(currencyCode, priceType);
    }

    private record ParsedSeries(String currencyCode, PriceType priceType) {
    }

    private enum PriceType {
        BUY,
        SELL
    }
}

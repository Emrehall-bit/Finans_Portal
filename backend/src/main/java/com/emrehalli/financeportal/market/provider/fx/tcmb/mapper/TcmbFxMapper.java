package com.emrehalli.financeportal.market.provider.fx.tcmb.mapper;

import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbEvdsResponse;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbFxResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TcmbFxMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public TcmbFxResponse toFxResponse(TcmbEvdsResponse response) {
        TcmbFxResponse fxResponse = new TcmbFxResponse();
        if (response != null && response.getItems() != null) {
            fxResponse.setItems(new ArrayList<>(response.getItems()));
        }
        return fxResponse;
    }

    public List<FxRateDto> toFxRates(TcmbFxResponse response, List<String> requestedSeriesCodes) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return List.of();
        }

        List<FxRateDto> rates = new ArrayList<>();
        for (Map<String, Object> item : response.getItems()) {
            LocalDateTime timestamp = parseItemDate(asText(item.get("Tarih")));
            if (timestamp == null) {
                continue;
            }

            for (String requestedSeriesCode : requestedSeriesCodes) {
                String responseSeriesCode = requestedSeriesCode.replace('.', '_');
                BigDecimal priceValue = parseDecimal(asText(item.get(responseSeriesCode)));
                if (priceValue == null) {
                    continue;
                }

                ParsedSeries parsedSeries = parseSeries(responseSeriesCode);
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

        return rates;
    }

    private LocalDateTime parseItemDate(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateText, DATE_FORMATTER).atStartOfDay();
        } catch (DateTimeParseException exception) {
            throw new DataProviderException("Failed to parse TCMB response date", exception);
        }
    }

    private BigDecimal parseDecimal(String valueText) {
        if (valueText == null || valueText.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(valueText.trim().replace(",", "."));
        } catch (NumberFormatException exception) {
            throw new DataProviderException("Failed to parse TCMB response value", exception);
        }
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private ParsedSeries parseSeries(String seriesCode) {
        String[] parts = seriesCode.split("_");
        String currencyCode = parts[2];
        PriceType priceType = "A".equals(parts[3]) ? PriceType.BUY : PriceType.SELL;
        return new ParsedSeries(currencyCode, priceType);
    }

    private record ParsedSeries(String currencyCode, PriceType priceType) {
    }

    private enum PriceType {
        BUY,
        SELL
    }
}


package com.emrehalli.financeportal.market.provider.fx.tcmb.mapper;

import com.emrehalli.financeportal.market.provider.fx.tcmb.TcmbFxSeriesDefinition;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbHistoricalFxValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TcmbHistoricalFxMapper {

    private static final String DATE_FIELD = "Tarih";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public List<TcmbHistoricalFxValue> mapRows(
            List<Map<String, Object>> rows,
            List<TcmbFxSeriesDefinition> definitions
    ) {
        if (rows == null || rows.isEmpty() || definitions == null || definitions.isEmpty()) {
            return List.of();
        }

        List<TcmbHistoricalFxValue> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            LocalDate priceDate = parseDate(asText(row.get(DATE_FIELD)));
            if (priceDate == null) {
                continue;
            }

            for (TcmbFxSeriesDefinition definition : definitions) {
                BigDecimal priceValue = parseDecimal(asText(row.get(definition.responseFieldName())));
                if (priceValue == null) {
                    continue;
                }

                values.add(new TcmbHistoricalFxValue(
                        definition.instrumentCode(),
                        definition.seriesCode(),
                        priceDate,
                        priceValue
                ));
            }
        }

        return values;
    }

    private LocalDate parseDate(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateText, DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String valueText) {
        if (valueText == null) {
            return null;
        }

        String normalized = valueText.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        normalized = normalized.replace(",", ".");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}





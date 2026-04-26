package com.emrehalli.financeportal.technicalanalysis.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MovingAverageService {

    public List<BigDecimal> calculateSimpleMovingAverage(List<BigDecimal> values, int period) {
        if (values == null || values.isEmpty() || period <= 0) {
            return Collections.emptyList();
        }

        List<BigDecimal> averages = new ArrayList<>(values.size());
        BigDecimal rollingSum = BigDecimal.ZERO;

        for (int index = 0; index < values.size(); index++) {
            BigDecimal value = sanitize(values.get(index));
            rollingSum = rollingSum.add(value);

            if (index >= period) {
                rollingSum = rollingSum.subtract(sanitize(values.get(index - period)));
            }

            if (index + 1 < period) {
                averages.add(null);
                continue;
            }

            averages.add(rollingSum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP));
        }

        return Collections.unmodifiableList(new ArrayList<>(averages));
    }

    private BigDecimal sanitize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

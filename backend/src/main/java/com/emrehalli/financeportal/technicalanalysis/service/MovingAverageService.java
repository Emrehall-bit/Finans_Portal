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
        int validCount = 0;

        for (int index = 0; index < values.size(); index++) {
            BigDecimal value = values.get(index);
            if (value != null) {
                rollingSum = rollingSum.add(value);
                validCount++;
            }

            if (index >= period) {
                BigDecimal dropped = values.get(index - period);
                if (dropped != null) {
                    rollingSum = rollingSum.subtract(dropped);
                    validCount--;
                }
            }

            if (index + 1 < period || validCount < period) {
                averages.add(null);
                continue;
            }

            averages.add(rollingSum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP));
        }

        return Collections.unmodifiableList(new ArrayList<>(averages));
    }
}




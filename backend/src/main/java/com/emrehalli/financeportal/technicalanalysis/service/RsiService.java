package com.emrehalli.financeportal.technicalanalysis.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class RsiService {

    private static final int DIVISION_SCALE = 8;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public List<BigDecimal> calculateRsi(List<BigDecimal> values, int period) {
        if (values == null || values.isEmpty() || period <= 0) {
            return Collections.emptyList();
        }

        List<BigDecimal> rsiValues = new ArrayList<>(Collections.nCopies(values.size(), null));
        if (values.size() <= period) {
            return Collections.unmodifiableList(rsiValues);
        }

        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;

        for (int index = 1; index <= period; index++) {
            BigDecimal change = safe(values.get(index)).subtract(safe(values.get(index - 1)));
            if (change.signum() > 0) {
                gainSum = gainSum.add(change);
            } else if (change.signum() < 0) {
                lossSum = lossSum.add(change.abs());
            }
        }

        BigDecimal averageGain = gainSum.divide(BigDecimal.valueOf(period), DIVISION_SCALE, RoundingMode.HALF_UP);
        BigDecimal averageLoss = lossSum.divide(BigDecimal.valueOf(period), DIVISION_SCALE, RoundingMode.HALF_UP);
        rsiValues.set(period, toRsi(averageGain, averageLoss));

        for (int index = period + 1; index < values.size(); index++) {
            BigDecimal change = safe(values.get(index)).subtract(safe(values.get(index - 1)));
            BigDecimal gain = change.signum() > 0 ? change : BigDecimal.ZERO;
            BigDecimal loss = change.signum() < 0 ? change.abs() : BigDecimal.ZERO;

            averageGain = averageGain.multiply(BigDecimal.valueOf(period - 1L))
                    .add(gain)
                    .divide(BigDecimal.valueOf(period), DIVISION_SCALE, RoundingMode.HALF_UP);
            averageLoss = averageLoss.multiply(BigDecimal.valueOf(period - 1L))
                    .add(loss)
                    .divide(BigDecimal.valueOf(period), DIVISION_SCALE, RoundingMode.HALF_UP);

            rsiValues.set(index, toRsi(averageGain, averageLoss));
        }

        return Collections.unmodifiableList(rsiValues);
    }

    private BigDecimal toRsi(BigDecimal averageGain, BigDecimal averageLoss) {
        if (averageGain.signum() == 0 && averageLoss.signum() == 0) {
            return BigDecimal.valueOf(50);
        }

        if (averageLoss.signum() == 0) {
            return HUNDRED;
        }

        if (averageGain.signum() == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal relativeStrength = averageGain.divide(averageLoss, DIVISION_SCALE, RoundingMode.HALF_UP);
        BigDecimal denominator = BigDecimal.ONE.add(relativeStrength);
        return HUNDRED.subtract(
                HUNDRED.divide(denominator, DIVISION_SCALE, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

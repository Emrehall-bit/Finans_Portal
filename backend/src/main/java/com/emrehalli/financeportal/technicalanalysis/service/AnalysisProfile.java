package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;

import java.math.BigDecimal;

public record AnalysisProfile(
        BigDecimal shortTermMomentumThresholdPct,
        BigDecimal rangeTrendThresholdPct,
        BigDecimal rsiOverboughtLevel,
        BigDecimal rsiOversoldLevel
) {

    public static final AnalysisProfile DEFAULT = new AnalysisProfile(
            BigDecimal.valueOf(0.10),
            BigDecimal.ONE,
            BigDecimal.valueOf(70),
            BigDecimal.valueOf(30)
    );

    public static AnalysisProfile forType(InstrumentType type) {
        if (type == null) {
            return DEFAULT;
        }
        return switch (type) {
            case FX -> new AnalysisProfile(
                    BigDecimal.valueOf(0.05),
                    BigDecimal.valueOf(0.5),
                    BigDecimal.valueOf(70),
                    BigDecimal.valueOf(30)
            );
            case CRYPTO -> new AnalysisProfile(
                    BigDecimal.valueOf(0.50),
                    BigDecimal.valueOf(3.0),
                    BigDecimal.valueOf(75),
                    BigDecimal.valueOf(25)
            );
            case FUND -> new AnalysisProfile(
                    BigDecimal.valueOf(0.10),
                    BigDecimal.valueOf(0.5),
                    BigDecimal.valueOf(65),
                    BigDecimal.valueOf(35)
            );
            default -> DEFAULT;
        };
    }
}

package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class MarketDailyChangeService {

    private final MarketPriceHistoryRepository historyRepository;

    public BigDecimal calculate(
            MarketInstrument instrument,
            BigDecimal currentPrice,
            LocalDateTime currentTimestamp,
            SourceName sourceName
    ) {
        if (instrument == null || currentPrice == null || currentTimestamp == null) {
            return null;
        }

        SourceName resolvedSource = sourceName != null ? sourceName : instrument.getSourceName();
        var currentDayStart = currentTimestamp
                .toLocalDate()
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        var effectivePrice = currentPrice;
        var previousCloseCutoff = currentDayStart;

        var latestDailyClose = historyRepository
                .findTopByInstrumentAndIntervalTypeAndSourceNameOrderByPriceTimestampDesc(
                        instrument,
                        IntervalType.ONE_DAY,
                        resolvedSource
                )
                .or(() -> historyRepository.findTopByInstrumentAndIntervalTypeOrderByPriceTimestampDesc(
                        instrument,
                        IntervalType.ONE_DAY
                ));

        if (latestDailyClose.isPresent()) {
            MarketPriceHistory latestHistory = latestDailyClose.get();
            BigDecimal latestClose = latestHistory.getClosePrice();
            Instant latestHistoryTimestamp = latestHistory.getPriceTimestamp();
            boolean priceLooksStale = latestClose != null
                    && latestHistoryTimestamp != null
                    && latestHistoryTimestamp.isBefore(currentDayStart)
                    && currentPrice.compareTo(latestClose) == 0;

            if (priceLooksStale) {
                effectivePrice = latestClose;
                previousCloseCutoff = latestHistoryTimestamp;
            }
        }

        final BigDecimal priceForChange = effectivePrice;
        final Instant cutoffForChange = previousCloseCutoff;

        return historyRepository
                .findTopByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampLessThanOrderByPriceTimestampDesc(
                        instrument,
                        IntervalType.ONE_DAY,
                        resolvedSource,
                        cutoffForChange
                )
                .or(() -> historyRepository.findTopByInstrumentAndIntervalTypeAndPriceTimestampLessThanOrderByPriceTimestampDesc(
                        instrument,
                        IntervalType.ONE_DAY,
                        cutoffForChange
                ))
                .map(MarketPriceHistory::getClosePrice)
                .filter(previousClose -> previousClose.compareTo(BigDecimal.ZERO) != 0)
                .map(previousClose -> priceForChange
                        .subtract(previousClose)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(previousClose, 4, RoundingMode.HALF_UP))
                .orElse(null);
    }
}

package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.market.service.MarketQueryService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class MarketHistoricalPriceReader implements HistoricalPriceReader {

    private final MarketQueryService marketQueryService;

    public MarketHistoricalPriceReader(MarketQueryService marketQueryService) {
        this.marketQueryService = marketQueryService;
    }

    @Override
    public List<HistoricalPricePoint> read(String symbol, LocalDate from, LocalDate to) {
        return marketQueryService.getHistory(symbol, null, from, to).stream()
                .map(point -> new HistoricalPricePoint(
                        point.symbol(),
                        point.priceDate(),
                        point.closePrice()
                ))
                .toList();
    }
}


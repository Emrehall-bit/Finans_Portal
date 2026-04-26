package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.market.service.MarketHistoryService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class MarketHistoricalPriceReader implements HistoricalPriceReader {

    private static final Logger logger = LogManager.getLogger(MarketHistoricalPriceReader.class);

    private final MarketHistoryService marketHistoryService;

    public MarketHistoricalPriceReader(MarketHistoryService marketHistoryService) {
        this.marketHistoryService = marketHistoryService;
    }

    @Override
    public List<HistoricalPricePoint> read(String symbol, LocalDate from, LocalDate to) {
        logger.info("Technical analysis history read started: symbol={}, from={}, to={}", symbol, from, to);

        List<HistoricalPricePoint> points = marketHistoryService.getHistory(symbol, from, to).stream()
                .map(record -> new HistoricalPricePoint(
                        record.symbol(),
                        record.priceDate(),
                        record.closePrice()
                ))
                .toList();

        logger.info(
                "Technical analysis history read completed: symbol={}, from={}, to={}, pointCount={}",
                symbol,
                from,
                to,
                points.size()
        );
        return points;
    }
}

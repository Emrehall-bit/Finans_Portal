package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only contract for retrieving market price history.
 */
public interface MarketHistoryReader {

    /**
     * Retrieves history for a symbol across all sources.
     *
     * @param symbol instrument symbol
     * @param startDate start date
     * @param endDate end date
     * @return history records
     */
    List<MarketHistoryRecord> getHistory(String symbol, LocalDate startDate, LocalDate endDate);

    /**
     * Retrieves history for a symbol with an optional source filter.
     *
     * @param symbol instrument symbol
     * @param source optional provider source
     * @param startDate start date
     * @param endDate end date
     * @return history records
     */
    List<MarketHistoryRecord> getHistory(String symbol, DataSource source, LocalDate startDate, LocalDate endDate);
}

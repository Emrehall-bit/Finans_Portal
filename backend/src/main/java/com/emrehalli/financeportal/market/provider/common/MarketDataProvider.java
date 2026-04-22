package com.emrehalli.financeportal.market.provider.common;

import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.InstrumentType;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface MarketDataProvider {

    String getProviderName();

    Set<InstrumentType> getSupportedInstrumentTypes();

    List<MarketDataDto> fetchSnapshotData();

    default List<MarketDataDto> fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        return List.of();
    }

    default boolean supportsHistoricalData() {
        return false;
    }

    default boolean isActive() {
        return true;
    }
}

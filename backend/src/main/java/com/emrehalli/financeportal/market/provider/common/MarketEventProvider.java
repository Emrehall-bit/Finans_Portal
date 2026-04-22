package com.emrehalli.financeportal.market.provider.common;

import com.emrehalli.financeportal.market.dto.event.MarketEventDto;

import java.util.List;

public interface MarketEventProvider {

    String getProviderName();

    List<MarketEventDto> fetchRecentEvents();

    default boolean isActive() {
        return true;
    }
}

package com.emrehalli.financeportal.market.provider.common;

import com.emrehalli.financeportal.market.dto.common.MarketDataDto;

import java.util.List;

public interface MarketDataProvider {

    String getProviderName();

    List<MarketDataDto> fetchMarketData();
}
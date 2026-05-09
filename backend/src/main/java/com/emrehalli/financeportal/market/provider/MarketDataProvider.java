package com.emrehalli.financeportal.market.provider;

import java.util.List;

/**
 * Contract for market data providers.
 */
public interface MarketDataProvider {

    String getSourceName();

    List<?> fetch();
}

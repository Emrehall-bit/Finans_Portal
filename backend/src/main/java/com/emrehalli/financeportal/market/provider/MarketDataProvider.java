package com.emrehalli.financeportal.market.provider;

import java.util.List;

public interface MarketDataProvider {

    String getSourceName();

    List<?> fetch();
}

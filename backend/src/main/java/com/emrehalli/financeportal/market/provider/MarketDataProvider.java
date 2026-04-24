package com.emrehalli.financeportal.market.provider;

import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.domain.enums.DataSource;

import java.util.List;

public interface MarketDataProvider {

    DataSource source();

    boolean supports(ProviderFetchRequest request);

    default ProviderFetchResult fetch(ProviderFetchRequest request) {
        return ProviderFetchResult.of(fetchQuotes(request));
    }

    List<MarketQuote> fetchQuotes(ProviderFetchRequest request);
}

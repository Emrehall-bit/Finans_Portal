package com.emrehalli.financeportal.market.provider.stock;

import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Provider chain for stock quote retrieval.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StockProviderChain implements MarketDataProvider {

    private final YahooFinanceStockProvider yahooProvider;

    @Override
    public String getSourceName() {
        return yahooProvider.getSourceName();
    }

    @Override
    public List<?> fetch() {
        try {
            List<?> result = yahooProvider.fetch();
            if (!result.isEmpty()) {
                return result;
            }
        } catch (DataProviderException exception) {
            log.warn("Yahoo Finance DataProviderException: {}", exception.getMessage(), exception);
        } catch (Exception exception) {
            log.error("Yahoo Finance beklenmedik hata: {}", exception.getMessage(), exception);
        }
        return Collections.emptyList();
    }
}

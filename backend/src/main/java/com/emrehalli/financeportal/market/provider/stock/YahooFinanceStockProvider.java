package com.emrehalli.financeportal.market.provider.stock;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.stock.dto.StockQuoteDto;
import com.emrehalli.financeportal.market.support.BistSymbolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Yahoo Finance stock market data provider.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class YahooFinanceStockProvider implements MarketDataProvider {

    private final MarketProperties props;
    private final RestTemplate restTemplate;
    private final BistSymbolRegistry bistSymbolRegistry;

    @Override
    public String getSourceName() {
        return SourceName.YAHOO_FINANCE.name();
    }

    @Override
    public List<StockQuoteDto> fetch() {
        try {
            List<String> symbols = bistSymbolRegistry.getBist30Symbols();
            List<StockQuoteDto> quotes = fetchFromApi(symbols);
            if (quotes.isEmpty()) {
                log.warn("Yahoo Finance stock provider returned no stock quote data for symbols {}", symbols);
            }
            return quotes;
        } catch (DataProviderException exception) {
            log.error("Failed to fetch stock quote data from Yahoo Finance", exception);
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to fetch stock quote data from Yahoo Finance", exception);
            throw new DataProviderException("Failed to fetch stock quote data from Yahoo Finance", exception);
        }
    }

    private List<StockQuoteDto> fetchFromApi(List<String> symbols) {
        // TODO: Yahoo Finance gayri resmi endpoint yapısı
        // kontrol edilerek doldurulacak.
        // Şimdilik boş liste dön.
        return Collections.emptyList();
    }
}

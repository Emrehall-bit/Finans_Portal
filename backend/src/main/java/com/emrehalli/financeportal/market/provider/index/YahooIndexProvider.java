package com.emrehalli.financeportal.market.provider.index;

import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
import com.emrehalli.financeportal.market.provider.yahoo.YahooFinanceQuoteClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Yahoo Finance provider for global stock index quotes.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class YahooIndexProvider implements MarketDataProvider {

    private final YahooFinanceQuoteClient quoteClient;
    private final IndexSymbolRegistry registry;

    @Override
    public String getSourceName() {
        return SourceName.YAHOO_FINANCE.name();
    }

    @Override
    public List<StockPriceDto> fetch() {
        try {
            List<YahooFinanceQuoteClient.YahooQuoteItem> raw = quoteClient.fetchQuotes(registry.getAllYahooSymbols());
            if (raw.isEmpty()) {
                log.warn("Yahoo Index provider returned no data");
                return List.of();
            }

            Instant now = Instant.now();
            List<StockPriceDto> result = new ArrayList<>();

            for (YahooFinanceQuoteClient.YahooQuoteItem item : raw) {
                String code = registry.toInstrumentCode(item.yahooSymbol());
                if (code == null) {
                    log.warn("Unknown Yahoo index symbol, skipping. yahooSymbol={}", item.yahooSymbol());
                    continue;
                }
                if (item.price() == null || item.price().signum() <= 0) {
                    log.warn("Skipping index quote with invalid price. code={}, price={}", code, item.price());
                    continue;
                }
                result.add(new StockPriceDto(
                        code,
                        item.yahooSymbol(),
                        item.price(),
                        item.changePercent(),
                        item.previousClose(),
                        item.dayHigh(),
                        item.dayLow(),
                        item.volume(),
                        SourceName.YAHOO_FINANCE.name(),
                        now
                ));
            }
            return result;

        } catch (DataProviderException e) {
            log.error("Yahoo index provider failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Yahoo index provider unexpected error", e);
            throw new DataProviderException("Failed to fetch index data from Yahoo Finance", e);
        }
    }
}




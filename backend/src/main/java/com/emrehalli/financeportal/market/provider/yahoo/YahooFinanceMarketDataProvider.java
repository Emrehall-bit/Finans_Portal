package com.emrehalli.financeportal.market.provider.yahoo;

import com.emrehalli.financeportal.config.YahooFinanceProperties;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.common.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.yahoo.client.YahooFinanceClient;
import com.emrehalli.financeportal.market.provider.yahoo.mapper.YahooFinanceMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class YahooFinanceMarketDataProvider implements MarketDataProvider {

    private static final Logger logger = LogManager.getLogger(YahooFinanceMarketDataProvider.class);

    private final YahooFinanceClient yahooFinanceClient;
    private final YahooFinanceMapper yahooFinanceMapper;
    private final YahooFinanceProperties yahooFinanceProperties;

    public YahooFinanceMarketDataProvider(YahooFinanceClient yahooFinanceClient,
                                          YahooFinanceMapper yahooFinanceMapper,
                                          YahooFinanceProperties yahooFinanceProperties) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.yahooFinanceMapper = yahooFinanceMapper;
        this.yahooFinanceProperties = yahooFinanceProperties;
    }

    @Override
    public String getProviderName() {
        return "YAHOO";
    }

    @Override
    public Set<InstrumentType> getSupportedInstrumentTypes() {
        return Set.of(InstrumentType.EQUITY, InstrumentType.INDEX);
    }

    @Override
    public List<MarketDataDto> fetchSnapshotData() {
        List<MarketDataDto> snapshots = yahooFinanceMapper.mapSnapshot(yahooFinanceClient.fetchSnapshotData());
        if (snapshots.isEmpty()) {
            logger.info("Yahoo Finance provider returned no market data after live snapshot fetch.");
        }
        return snapshots;
    }

    @Override
    public List<MarketDataDto> fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        List<MarketDataDto> history = yahooFinanceMapper.mapHistorical(yahooFinanceClient.fetchHistoricalData(symbol, startDate, endDate), symbol);
        if (history.isEmpty()) {
            logger.info("Yahoo Finance provider returned no historical data for {} because historical integration is not implemented yet.", symbol);
        }
        return history;
    }

    @Override
    public boolean supportsHistoricalData() {
        return true;
    }

    @Override
    public boolean isActive() {
        return yahooFinanceProperties.isEnabled();
    }
}

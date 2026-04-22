package com.emrehalli.financeportal.market.provider.binance;

import com.emrehalli.financeportal.config.BinanceProperties;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.binance.client.BinanceClient;
import com.emrehalli.financeportal.market.provider.binance.mapper.BinanceMapper;
import com.emrehalli.financeportal.market.provider.common.MarketDataProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class BinanceMarketDataProvider implements MarketDataProvider {

    private static final Logger logger = LogManager.getLogger(BinanceMarketDataProvider.class);

    private final BinanceClient binanceClient;
    private final BinanceMapper binanceMapper;
    private final BinanceProperties binanceProperties;

    public BinanceMarketDataProvider(BinanceClient binanceClient,
                                     BinanceMapper binanceMapper,
                                     BinanceProperties binanceProperties) {
        this.binanceClient = binanceClient;
        this.binanceMapper = binanceMapper;
        this.binanceProperties = binanceProperties;
    }

    @Override
    public String getProviderName() {
        return "BINANCE";
    }

    @Override
    public Set<InstrumentType> getSupportedInstrumentTypes() {
        return Set.of(InstrumentType.CRYPTO);
    }

    @Override
    public List<MarketDataDto> fetchSnapshotData() {
        List<MarketDataDto> snapshots = binanceMapper.mapSnapshot(binanceClient.fetchSnapshotData());
        if (snapshots.isEmpty()) {
            logger.info("Binance provider returned no market data after live snapshot fetch.");
        }
        return snapshots;
    }

    @Override
    public List<MarketDataDto> fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        List<MarketDataDto> history = binanceMapper.mapHistorical(binanceClient.fetchHistoricalData(symbol, startDate, endDate), symbol);
        if (history.isEmpty()) {
            logger.info("Binance provider returned no historical data for {} because historical integration is not implemented yet.", symbol);
        }
        return history;
    }

    @Override
    public boolean supportsHistoricalData() {
        return true;
    }

    @Override
    public boolean isActive() {
        return binanceProperties.isEnabled();
    }
}

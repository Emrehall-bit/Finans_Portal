package com.emrehalli.financeportal.market.provider.tefas;

import com.emrehalli.financeportal.config.TefasProperties;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.common.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.tefas.client.TefasClient;
import com.emrehalli.financeportal.market.provider.tefas.mapper.TefasMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class TefasMarketDataProvider implements MarketDataProvider {

    private static final Logger logger = LogManager.getLogger(TefasMarketDataProvider.class);

    private final TefasClient tefasClient;
    private final TefasMapper tefasMapper;
    private final TefasProperties tefasProperties;

    public TefasMarketDataProvider(TefasClient tefasClient,
                                   TefasMapper tefasMapper,
                                   TefasProperties tefasProperties) {
        this.tefasClient = tefasClient;
        this.tefasMapper = tefasMapper;
        this.tefasProperties = tefasProperties;
    }

    @Override
    public String getProviderName() {
        return "TEFAS";
    }

    @Override
    public Set<InstrumentType> getSupportedInstrumentTypes() {
        return Set.of(InstrumentType.FUND);
    }

    @Override
    public List<MarketDataDto> fetchSnapshotData() {
        List<MarketDataDto> snapshots = tefasMapper.mapSnapshot(tefasClient.fetchSnapshotData());
        if (snapshots.isEmpty()) {
            logger.info("TEFAS provider returned no market data after live snapshot fetch.");
        }
        return snapshots;
    }

    @Override
    public List<MarketDataDto> fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        List<MarketDataDto> history = tefasMapper.mapHistorical(tefasClient.fetchHistoricalData(symbol, startDate, endDate), symbol);
        if (history.isEmpty()) {
            logger.info("TEFAS provider returned no historical data for {} because historical integration is not implemented yet.", symbol);
        }
        return history;
    }

    @Override
    public boolean supportsHistoricalData() {
        return true;
    }

    @Override
    public boolean isActive() {
        return tefasProperties.isEnabled();
    }
}

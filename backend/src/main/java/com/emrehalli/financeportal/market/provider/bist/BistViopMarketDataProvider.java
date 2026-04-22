package com.emrehalli.financeportal.market.provider.bist;

import com.emrehalli.financeportal.config.BistProperties;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.enums.InstrumentType;
import com.emrehalli.financeportal.market.provider.bist.client.BistViopClient;
import com.emrehalli.financeportal.market.provider.bist.mapper.BistViopMapper;
import com.emrehalli.financeportal.market.provider.common.MarketDataProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class BistViopMarketDataProvider implements MarketDataProvider {

    private static final Logger logger = LogManager.getLogger(BistViopMarketDataProvider.class);

    private final BistViopClient bistViopClient;
    private final BistViopMapper bistViopMapper;
    private final BistProperties bistProperties;

    public BistViopMarketDataProvider(BistViopClient bistViopClient,
                                      BistViopMapper bistViopMapper,
                                      BistProperties bistProperties) {
        this.bistViopClient = bistViopClient;
        this.bistViopMapper = bistViopMapper;
        this.bistProperties = bistProperties;
    }

    @Override
    public String getProviderName() {
        return "BIST";
    }

    @Override
    public Set<InstrumentType> getSupportedInstrumentTypes() {
        return Set.of(InstrumentType.EQUITY, InstrumentType.FUTURE, InstrumentType.DERIVATIVE, InstrumentType.INDEX);
    }

    @Override
    public List<MarketDataDto> fetchSnapshotData() {
        List<MarketDataDto> snapshots = bistViopMapper.mapSnapshot(bistViopClient.fetchSnapshotData());
        if (snapshots.isEmpty()) {
            logger.info("BIST/VIOP provider returned no reference data after live fetch.");
        }
        return snapshots;
    }

    @Override
    public List<MarketDataDto> fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        List<MarketDataDto> history = bistViopMapper.mapHistorical(bistViopClient.fetchHistoricalData(symbol, startDate, endDate), symbol);
        if (history.isEmpty()) {
            logger.info("BIST/VIOP provider returned no historical data for {} because historical integration is not implemented yet.", symbol);
        }
        return history;
    }

    @Override
    public boolean supportsHistoricalData() {
        return true;
    }

    @Override
    public boolean isActive() {
        return bistProperties.isEnabled();
    }
}

package com.emrehalli.financeportal.market.provider.kap;

import com.emrehalli.financeportal.config.KapProperties;
import com.emrehalli.financeportal.market.dto.event.MarketEventDto;
import com.emrehalli.financeportal.market.provider.common.MarketEventProvider;
import com.emrehalli.financeportal.market.provider.kap.client.KapClient;
import com.emrehalli.financeportal.market.provider.kap.mapper.KapMarketEventMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KapMarketEventProvider implements MarketEventProvider {

    private static final Logger logger = LogManager.getLogger(KapMarketEventProvider.class);

    private final KapClient kapClient;
    private final KapMarketEventMapper kapMarketEventMapper;
    private final KapProperties kapProperties;

    public KapMarketEventProvider(KapClient kapClient,
                                  KapMarketEventMapper kapMarketEventMapper,
                                  KapProperties kapProperties) {
        this.kapClient = kapClient;
        this.kapMarketEventMapper = kapMarketEventMapper;
        this.kapProperties = kapProperties;
    }

    @Override
    public String getProviderName() {
        return "KAP";
    }

    @Override
    public List<MarketEventDto> fetchRecentEvents() {
        List<MarketEventDto> events = kapMarketEventMapper.map(kapClient.fetchRecentDisclosures());
        if (events.isEmpty()) {
            logger.info("KAP provider returned no market events after live fetch.");
        }
        return events;
    }

    @Override
    public boolean isActive() {
        return kapProperties.isEnabled();
    }
}

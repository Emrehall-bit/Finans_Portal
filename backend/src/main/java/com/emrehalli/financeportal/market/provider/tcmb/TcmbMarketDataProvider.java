package com.emrehalli.financeportal.market.provider.tcmb;

import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.provider.common.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.tcmb.client.EvdsClient;
import com.emrehalli.financeportal.market.provider.tcmb.dto.EvdsResponse;
import com.emrehalli.financeportal.market.provider.tcmb.mapper.EvdsMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TcmbMarketDataProvider implements MarketDataProvider {

    private static final Logger logger = LogManager.getLogger(TcmbMarketDataProvider.class);

    private final EvdsClient client;
    private final EvdsMapper mapper;
    private final ObjectMapper objectMapper;

    public TcmbMarketDataProvider(EvdsClient client, EvdsMapper mapper, ObjectMapper objectMapper) {
        this.client = client;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "EVDS";
    }

    @Override
    public List<MarketDataDto> fetchMarketData() {
        try {
            logger.info("Fetching TCMB market data from EVDS...");

            String json = client.fetchCurrencyData();

            // Güvenlik kontrolü (Null veya boş dönme ihtimaline karşı uygulamanın çökmesini engeller)
            if (json == null || json.isBlank()) {
                logger.warn("EVDS response body is null or blank");
                return List.of();
            }

            logger.info("EVDS response received successfully.");

            EvdsResponse response = objectMapper.readValue(json, EvdsResponse.class);

            logger.info("EVDS response parsed. Item count: {}",
                    response.getItems() != null ? response.getItems().size() : 0);

            List<MarketDataDto> mappedData = mapper.map(response);

            logger.info("Mapped TCMB data count: {}", mappedData.size());

            return mappedData;
        } catch (Exception e) {
            logger.error("Error while fetching/parsing EVDS data", e);
            throw new RuntimeException("EVDS data parsing failed: " + e.getMessage(), e);
        }
    }
}
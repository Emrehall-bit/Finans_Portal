package com.emrehalli.financeportal.market.provider.futures;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.futures.dto.FuturesContractDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * BIST VIOP market data provider.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ViopDataProvider implements MarketDataProvider {

    private final MarketProperties props;
    private final RestTemplate restTemplate;

    @Override
    public String getSourceName() {
        return SourceName.BIST.name();
    }

    @Override
    public List<FuturesContractDto> fetch() {
        try {
            List<FuturesContractDto> contracts = fetchFromApi();
            if (contracts.isEmpty()) {
                log.warn("BIST VIOP provider returned no futures contract data.");
            }
            return contracts;
        } catch (DataProviderException exception) {
            log.error("Failed to fetch VIOP contract data from BIST", exception);
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to fetch VIOP contract data from BIST", exception);
            throw new DataProviderException("Failed to fetch VIOP contract data from BIST", exception);
        }
    }

    private List<FuturesContractDto> fetchFromApi() {
        // TODO: Borsa Ä°stanbul aÃ§Ä±k veri portalÄ±ndan
        // (datastore.borsaistanbul.com) endpoint kontrol
        // edilerek doldurulacak. Veri 15 dk gecikmeli.
        return Collections.emptyList();
    }
}





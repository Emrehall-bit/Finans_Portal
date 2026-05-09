package com.emrehalli.financeportal.market.provider.bond;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.bond.dto.BondRateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * TCMB bond market data provider.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BondDataProvider implements MarketDataProvider {

    private final MarketProperties props;
    private final RestTemplate restTemplate;

    @Override
    public String getSourceName() {
        return SourceName.TCMB.name();
    }

    @Override
    public List<BondRateDto> fetch() {
        try {
            List<BondRateDto> rates = fetchFromApi();
            if (rates.isEmpty()) {
                log.warn("TCMB bond provider returned no bond rate data. Weekend or holiday assumed.");
            }
            return rates;
        } catch (DataProviderException exception) {
            log.error("Failed to fetch bond rate data from TCMB", exception);
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to fetch bond rate data from TCMB", exception);
            throw new DataProviderException("Failed to fetch bond rate data from TCMB", exception);
        }
    }

    private List<BondRateDto> fetchFromApi() {
        // TODO: TCMB EVDS üzerinden tahvil/bono faiz serileri
        // farklı seri kodları ile çekilecek.
        // TcmbFxProvider ile aynı HTTP yapısı kullanılabilir.
        return Collections.emptyList();
    }
}

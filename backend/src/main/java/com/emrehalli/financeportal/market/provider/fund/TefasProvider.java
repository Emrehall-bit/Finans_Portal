package com.emrehalli.financeportal.market.provider.fund;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.fund.dto.FundNavDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * TEFAS fund market data provider.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TefasProvider implements MarketDataProvider {

    private final MarketProperties props;
    private final RestTemplate restTemplate;

    @Override
    public String getSourceName() {
        return SourceName.TEFAS.name();
    }

    @Override
    public List<FundNavDto> fetch() {
        try {
            List<FundNavDto> funds = fetchFromApi();
            if (funds.isEmpty()) {
                log.warn("TEFAS provider returned no fund NAV data.");
            }
            return funds;
        } catch (DataProviderException exception) {
            log.error("Failed to fetch fund NAV data from TEFAS", exception);
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to fetch fund NAV data from TEFAS", exception);
            throw new DataProviderException("Failed to fetch fund NAV data from TEFAS", exception);
        }
    }

    private List<FundNavDto> fetchFromApi() {
        // TODO: TEFAS endpoint yapisi degisken oldugundan
        // resmi kaynaktan kontrol edilerek doldurulacak.
        // Simdilik bos liste don.
        return Collections.emptyList();
    }
}

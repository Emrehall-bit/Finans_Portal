package com.emrehalli.financeportal.market.provider.fx;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Akbank FX market data provider.
 */
@Component
@Slf4j
public class AkbankFxProvider extends AbstractFxProvider implements MarketDataProvider {

    private static final String ENDPOINT = "/api/v2/investment/currency/exchangerates";

    private final MarketProperties props;

    public AkbankFxProvider(RestTemplate restTemplate, ObjectMapper objectMapper, MarketProperties props) {
        super(restTemplate, objectMapper);
        this.props = props;
    }

    @Override
    public String getSourceName() {
        return SourceName.AKBANK.name();
    }

    @Override
    public List<FxRateDto> fetch() {
        return fetchRates(
                props.getProviders().getAkbank().getBaseUrl() + ENDPOINT,
                "apikey",
                props.getProviders().getAkbank().getApiKey(),
                SourceName.AKBANK
        );
    }
}

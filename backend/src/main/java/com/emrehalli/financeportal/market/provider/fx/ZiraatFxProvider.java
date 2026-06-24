package com.emrehalli.financeportal.market.provider.fx;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Ziraat FX market data provider.
 */
@Component
@Slf4j
public class ZiraatFxProvider extends AbstractFxProvider implements MarketDataProvider {

    private static final String ENDPOINT = "/portal/treasure/exchangerates/query";

    private final MarketProperties props;

    public ZiraatFxProvider(RestTemplate restTemplate, ObjectMapper objectMapper, MarketProperties props) {
        super(restTemplate, objectMapper);
        this.props = props;
    }

    @Override
    public String getSourceName() {
        return SourceName.ZIRAAT.name();
    }

    @CircuitBreaker(name = "ziraatFx", fallbackMethod = "fetchFallback")
    @Override
    public List<FxRateDto> fetch() {
        return fetchRates(
                props.getProviders().getZiraat().getBaseUrl() + ENDPOINT,
                "apikey",
                props.getProviders().getZiraat().getApiKey(),
                SourceName.ZIRAAT
        );
    }

    private List<FxRateDto> fetchFallback(Throwable throwable) {
        log.warn("Ziraat FX circuit breaker fallback triggered, returning empty rate list. reason={}", throwable.toString());
        return List.of();
    }
}


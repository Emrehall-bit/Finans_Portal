package com.emrehalli.financeportal.market.provider.bist.client;

import com.emrehalli.financeportal.market.provider.bist.config.BistProviderProperties;
import com.emrehalli.financeportal.market.provider.bist.dto.BistQuoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BistDelayedClient {

    private static final Logger log = LoggerFactory.getLogger(BistDelayedClient.class);

    private final BistProviderProperties properties;

    public BistDelayedClient(BistProviderProperties properties) {
        this.properties = properties;
    }

    public List<BistQuoteResponse> fetchQuotes(List<String> symbols) {
        log.info(
                "BIST delayed fallback skipped: enabled={}, requestedSymbolCount={}",
                properties.getDelayed().isEnabled(),
                symbols == null ? 0 : symbols.size()
        );
        return List.of();
    }
}

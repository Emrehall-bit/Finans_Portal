package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.service.model.CurrentPriceSnapshot;

/**
 * Read-only contract for resolving the current market price of an instrument.
 */
public interface MarketPriceReader {

    /**
     * Resolves the current price snapshot for the given symbol.
     *
     * @param symbol instrument symbol
     * @return current price snapshot
     */
    CurrentPriceSnapshot resolveCurrentPrice(String symbol);
}

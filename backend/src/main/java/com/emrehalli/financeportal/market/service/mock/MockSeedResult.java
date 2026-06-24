package com.emrehalli.financeportal.market.service.mock;

/**
 * Summary statistics returned after a mock derivatives seed run.
 */
public record MockSeedResult(
        int futuresInstruments,
        int bondInstruments,
        int priceRecords,
        int historyRecords
) {
    public int totalInstruments() {
        return futuresInstruments + bondInstruments;
    }
}

package com.emrehalli.financeportal.market.support;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Registry for BIST stock symbols.
 */
@Component
public class BistSymbolRegistry {

    public List<String> getBist30Symbols() {
        // TODO: BIST-30 sembol listesi resmi Borsa İstanbul
        // verilerinden alınarak buraya eklenecek.
        return Collections.emptyList();
    }
}

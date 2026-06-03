package com.emrehalli.financeportal.market.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoWhitelistTest {

    private CryptoWhitelist whitelist;

    @BeforeEach
    void setUp() {
        whitelist = new CryptoWhitelist();
    }

    @Test
    void whitelisted_base_assets_are_accepted() {
        assertThat(whitelist.isWhitelisted("BTC")).isTrue();
        assertThat(whitelist.isWhitelisted("ETH")).isTrue();
        assertThat(whitelist.isWhitelisted("BNB")).isTrue();
        assertThat(whitelist.isWhitelisted("SOL")).isTrue();
    }

    @Test
    void usdt_is_included() {
        assertThat(whitelist.isWhitelisted("USDT")).isTrue();
    }

    @Test
    void non_whitelisted_base_asset_is_rejected() {
        assertThat(whitelist.isWhitelisted("XYZ")).isFalse();
        assertThat(whitelist.isWhitelisted("FAKECOIN")).isFalse();
        assertThat(whitelist.isWhitelisted("SCAM")).isFalse();
    }

    @Test
    void lookup_is_case_insensitive() {
        assertThat(whitelist.isWhitelisted("btc")).isTrue();
        assertThat(whitelist.isWhitelisted("Eth")).isTrue();
        assertThat(whitelist.isWhitelisted("usdt")).isTrue();
    }

    @Test
    void null_and_blank_inputs_are_rejected() {
        assertThat(whitelist.isWhitelisted(null)).isFalse();
        assertThat(whitelist.isWhitelisted("")).isFalse();
        assertThat(whitelist.isWhitelisted("   ")).isFalse();
    }

    @Test
    void try_pair_base_asset_extraction_accepts_whitelisted_coins() {
        // Simulates BinancePairMapper: symbol.substring(0, symbol.length() - 3)
        assertThat(extractAndCheck("BTCTRY")).isTrue();
        assertThat(extractAndCheck("ETHTRY")).isTrue();
        assertThat(extractAndCheck("USDTTRY")).isTrue();
        assertThat(extractAndCheck("SOLTRY")).isTrue();
    }

    @Test
    void try_pair_base_asset_extraction_rejects_non_whitelisted_coins() {
        assertThat(extractAndCheck("XYZTRY")).isFalse();
        assertThat(extractAndCheck("FAKECOINTRY")).isFalse();
    }

    @Test
    void getAll_returns_full_whitelist() {
        assertThat(whitelist.getAll()).hasSize(171);
        assertThat(whitelist.getAll()).contains("BTC", "ETH", "USDT", "DOGE", "SOL");
    }

    private boolean extractAndCheck(String binanceSymbol) {
        String baseAsset = binanceSymbol.substring(0, binanceSymbol.length() - 3);
        return whitelist.isWhitelisted(baseAsset);
    }
}

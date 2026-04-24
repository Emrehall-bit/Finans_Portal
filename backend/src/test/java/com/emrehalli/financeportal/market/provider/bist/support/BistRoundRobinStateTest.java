package com.emrehalli.financeportal.market.provider.bist.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BistRoundRobinStateTest {

    private final BistRoundRobinState state = new BistRoundRobinState();

    @Test
    void returnsSequentialBatchesAndWrapsAroundAfterSuccesses() {
        List<String> symbols = List.of("THYAO", "ASELS", "GARAN", "AKBNK", "BIMAS");

        var batch1 = state.nextBatch(symbols, 2);
        state.markSuccess(symbols, 2);
        var batch2 = state.nextBatch(symbols, 2);
        state.markSuccess(symbols, 2);
        var batch3 = state.nextBatch(symbols, 2);
        state.markSuccess(symbols, 2);
        var batch4 = state.nextBatch(symbols, 2);

        assertThat(batch1.startIndex()).isEqualTo(0);
        assertThat(batch1.symbols()).containsExactly("THYAO", "ASELS");
        assertThat(batch2.startIndex()).isEqualTo(2);
        assertThat(batch2.symbols()).containsExactly("GARAN", "AKBNK");
        assertThat(batch3.startIndex()).isEqualTo(4);
        assertThat(batch3.symbols()).containsExactly("BIMAS");
        assertThat(batch4.startIndex()).isEqualTo(0);
        assertThat(batch4.symbols()).containsExactly("THYAO", "ASELS");
    }

    @Test
    void doesNotAdvanceWhenMarkSuccessIsNotCalled() {
        List<String> symbols = List.of("THYAO", "ASELS", "GARAN");

        var first = state.nextBatch(symbols, 2);
        var second = state.nextBatch(symbols, 2);

        assertThat(first.startIndex()).isEqualTo(0);
        assertThat(second.startIndex()).isEqualTo(0);
        assertThat(second.symbols()).containsExactly("THYAO", "ASELS");
    }
}

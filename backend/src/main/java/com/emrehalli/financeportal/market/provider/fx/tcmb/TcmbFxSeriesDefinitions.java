package com.emrehalli.financeportal.market.provider.fx.tcmb;

import java.util.List;

public final class TcmbFxSeriesDefinitions {

    public static final TcmbFxSeriesDefinition USDTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:USD:BUY", "TP.DK.USD.A.YTL", "USD BUY");

    public static final TcmbFxSeriesDefinition USDTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:USD:SELL", "TP.DK.USD.S.YTL", "USD SELL");

    public static final List<TcmbFxSeriesDefinition> DEFAULT_DEFINITIONS = List.of(
            USDTRY_BUY,
            USDTRY_SELL
    );

    private TcmbFxSeriesDefinitions() {
    }
}

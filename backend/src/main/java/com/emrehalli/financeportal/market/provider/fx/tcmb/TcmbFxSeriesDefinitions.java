package com.emrehalli.financeportal.market.provider.fx.tcmb;

import java.util.List;

public final class TcmbFxSeriesDefinitions {

    public static final TcmbFxSeriesDefinition AUDTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:AUD:SELL", "TP.DK.AUD.S.YTL", "AUD SELL");
    public static final TcmbFxSeriesDefinition AZNTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:AZN:SELL", "TP.DK.AZN.S.YTL", "AZN SELL");
    public static final TcmbFxSeriesDefinition CADTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:CAD:SELL", "TP.DK.CAD.S.YTL", "CAD SELL");
    public static final TcmbFxSeriesDefinition CHFTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:CHF:SELL", "TP.DK.CHF.S.YTL", "CHF SELL");
    public static final TcmbFxSeriesDefinition CNYTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:CNY:SELL", "TP.DK.CNY.S.YTL", "CNY SELL");
    public static final TcmbFxSeriesDefinition EURTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:EUR:SELL", "TP.DK.EUR.S.YTL", "EUR SELL");
    public static final TcmbFxSeriesDefinition GBPTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:GBP:SELL", "TP.DK.GBP.S.YTL", "GBP SELL");
    public static final TcmbFxSeriesDefinition JPYTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:JPY:SELL", "TP.DK.JPY.S.YTL", "JPY SELL");
    public static final TcmbFxSeriesDefinition KRWTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:KRW:SELL", "TP.DK.KRW.S.YTL", "KRW SELL");
    public static final TcmbFxSeriesDefinition KWDTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:KWD:SELL", "TP.DK.KWD.S.YTL", "KWD SELL");
    public static final TcmbFxSeriesDefinition QARTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:QAR:SELL", "TP.DK.QAR.S.YTL", "QAR SELL");
    public static final TcmbFxSeriesDefinition RUBTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:RUB:SELL", "TP.DK.RUB.S.YTL", "RUB SELL");
    public static final TcmbFxSeriesDefinition USDTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:USD:SELL", "TP.DK.USD.S.YTL", "USD SELL");

    public static final List<TcmbFxSeriesDefinition> DEFAULT_DEFINITIONS = List.of(
            AUDTRY_SELL,
            AZNTRY_SELL,
            CADTRY_SELL,
            CHFTRY_SELL,
            CNYTRY_SELL,
            EURTRY_SELL,
            GBPTRY_SELL,
            JPYTRY_SELL,
            KRWTRY_SELL,
            KWDTRY_SELL,
            QARTRY_SELL,
            RUBTRY_SELL,
            USDTRY_SELL
    );

    private TcmbFxSeriesDefinitions() {
    }
}

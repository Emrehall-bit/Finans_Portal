package com.emrehalli.financeportal.market.provider.fx.tcmb;

import java.util.List;

public final class TcmbFxSeriesDefinitions {

    public static final TcmbFxSeriesDefinition AUDTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:AUD:BUY", "TP.DK.AUD.A.YTL", "AUD BUY");
    public static final TcmbFxSeriesDefinition AUDTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:AUD:SELL", "TP.DK.AUD.S.YTL", "AUD SELL");
    public static final TcmbFxSeriesDefinition AZNTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:AZN:BUY", "TP.DK.AZN.A.YTL", "AZN BUY");
    public static final TcmbFxSeriesDefinition AZNTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:AZN:SELL", "TP.DK.AZN.S.YTL", "AZN SELL");
    public static final TcmbFxSeriesDefinition CADTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:CAD:BUY", "TP.DK.CAD.A.YTL", "CAD BUY");
    public static final TcmbFxSeriesDefinition CADTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:CAD:SELL", "TP.DK.CAD.S.YTL", "CAD SELL");
    public static final TcmbFxSeriesDefinition CHFTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:CHF:BUY", "TP.DK.CHF.A.YTL", "CHF BUY");
    public static final TcmbFxSeriesDefinition CHFTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:CHF:SELL", "TP.DK.CHF.S.YTL", "CHF SELL");
    public static final TcmbFxSeriesDefinition CNYTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:CNY:BUY", "TP.DK.CNY.A.YTL", "CNY BUY");
    public static final TcmbFxSeriesDefinition CNYTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:CNY:SELL", "TP.DK.CNY.S.YTL", "CNY SELL");
    public static final TcmbFxSeriesDefinition EURTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:EUR:BUY", "TP.DK.EUR.A.YTL", "EUR BUY");
    public static final TcmbFxSeriesDefinition EURTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:EUR:SELL", "TP.DK.EUR.S.YTL", "EUR SELL");
    public static final TcmbFxSeriesDefinition GBPTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:GBP:BUY", "TP.DK.GBP.A.YTL", "GBP BUY");
    public static final TcmbFxSeriesDefinition GBPTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:GBP:SELL", "TP.DK.GBP.S.YTL", "GBP SELL");
    public static final TcmbFxSeriesDefinition JPYTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:JPY:BUY", "TP.DK.JPY.A.YTL", "JPY BUY");
    public static final TcmbFxSeriesDefinition JPYTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:JPY:SELL", "TP.DK.JPY.S.YTL", "JPY SELL");
    public static final TcmbFxSeriesDefinition KRWTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:KRW:BUY", "TP.DK.KRW.A.YTL", "KRW BUY");
    public static final TcmbFxSeriesDefinition KRWTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:KRW:SELL", "TP.DK.KRW.S.YTL", "KRW SELL");
    public static final TcmbFxSeriesDefinition KWDTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:KWD:BUY", "TP.DK.KWD.A.YTL", "KWD BUY");
    public static final TcmbFxSeriesDefinition KWDTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:KWD:SELL", "TP.DK.KWD.S.YTL", "KWD SELL");
    public static final TcmbFxSeriesDefinition QARTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:QAR:BUY", "TP.DK.QAR.A.YTL", "QAR BUY");
    public static final TcmbFxSeriesDefinition QARTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:QAR:SELL", "TP.DK.QAR.S.YTL", "QAR SELL");
    public static final TcmbFxSeriesDefinition RUBTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:RUB:BUY", "TP.DK.RUB.A.YTL", "RUB BUY");
    public static final TcmbFxSeriesDefinition RUBTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:RUB:SELL", "TP.DK.RUB.S.YTL", "RUB SELL");
    public static final TcmbFxSeriesDefinition USDTRY_BUY =
            TcmbFxSeriesDefinition.of("TCMB:USD:BUY", "TP.DK.USD.A.YTL", "USD BUY");
    public static final TcmbFxSeriesDefinition USDTRY_SELL =
            TcmbFxSeriesDefinition.of("TCMB:USD:SELL", "TP.DK.USD.S.YTL", "USD SELL");

    public static final List<TcmbFxSeriesDefinition> CURRENT_SYNC_DEFINITIONS = List.of(
            AUDTRY_BUY, AUDTRY_SELL,
            AZNTRY_BUY, AZNTRY_SELL,
            CADTRY_BUY, CADTRY_SELL,
            CHFTRY_BUY, CHFTRY_SELL,
            CNYTRY_BUY, CNYTRY_SELL,
            EURTRY_BUY, EURTRY_SELL,
            GBPTRY_BUY, GBPTRY_SELL,
            JPYTRY_BUY, JPYTRY_SELL,
            KRWTRY_BUY, KRWTRY_SELL,
            KWDTRY_BUY, KWDTRY_SELL,
            QARTRY_BUY, QARTRY_SELL,
            RUBTRY_BUY, RUBTRY_SELL,
            USDTRY_BUY, USDTRY_SELL
    );

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

package com.emrehalli.financeportal.market.provider.macro.tcmb;

import com.emrehalli.financeportal.market.domain.enums.MacroFrequency;
import com.emrehalli.financeportal.market.domain.enums.MacroUnit;
import com.emrehalli.financeportal.market.domain.enums.MacroValueType;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroIndicatorDef;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroSeriesField;
import com.emrehalli.financeportal.market.provider.macro.tcmb.dto.MacroSeriesRequest;

import java.util.List;

public final class TcmbMacroSeries {

    public static MacroSeriesRequest cpi(String startDate, String endDate) {
        return new MacroSeriesRequest(
                "TP.TUKFIY2025.GENEL-TP.TUKFIY2025.GENEL",
                List.of(
                        new MacroSeriesField("TP_TUKFIY2025_GENEL-1", MacroValueType.MONTHLY_CHANGE, "CPI_TR"),
                        new MacroSeriesField("TP_TUKFIY2025_GENEL-3", MacroValueType.YEARLY_CHANGE,  "CPI_TR")
                ),
                List.of(new MacroIndicatorDef("CPI_TR", "TÃœFE", MacroFrequency.MONTHLY, MacroUnit.PERCENT)),
                "avg-avg", "1-3", startDate, endDate, "0"
        );
    }

    public static MacroSeriesRequest ppi(String startDate, String endDate) {
        return new MacroSeriesRequest(
                "TP.TUFE1YI.T1-TP.TUFE1YI.T1",
                List.of(
                        new MacroSeriesField("TP_TUFE1YI_T1-1", MacroValueType.MONTHLY_CHANGE, "PPI_TR"),
                        new MacroSeriesField("TP_TUFE1YI_T1-3", MacroValueType.YEARLY_CHANGE,  "PPI_TR")
                ),
                List.of(new MacroIndicatorDef("PPI_TR", "YÄ°-ÃœFE", MacroFrequency.MONTHLY, MacroUnit.PERCENT)),
                "avg-avg", "1-3", startDate, endDate, "0"
        );
    }

    public static MacroSeriesRequest policyRate(String startDate, String endDate) {
        return new MacroSeriesRequest(
                "TP.BISPOLFAIZ.TUR",
                List.of(
                        new MacroSeriesField("TP_BISPOLFAIZ_TUR", MacroValueType.POLICY_RATE, "POLICY_RATE_TR")
                ),
                List.of(new MacroIndicatorDef("POLICY_RATE_TR", "TCMB Politika Faizi", MacroFrequency.MONTHLY, MacroUnit.PERCENT)),
                "last", "0", startDate, endDate, "0"
        );
    }

    public static MacroSeriesRequest laborMarket(String startDate, String endDate) {
        return new MacroSeriesRequest(
                "TP.YISGUCU2.G8-TP.YISGUCU2.G6",
                List.of(
                        new MacroSeriesField("TP_YISGUCU2_G8", MacroValueType.UNEMPLOYMENT_RATE,               "UNEMPLOYMENT_TR"),
                        new MacroSeriesField("TP_YISGUCU2_G6", MacroValueType.LABOR_FORCE_PARTICIPATION_RATE,  "LABOR_FORCE_PARTICIPATION_TR")
                ),
                List.of(
                        new MacroIndicatorDef("UNEMPLOYMENT_TR",              "Ä°ÅŸsizlik OranÄ±",         MacroFrequency.MONTHLY, MacroUnit.PERCENT),
                        new MacroIndicatorDef("LABOR_FORCE_PARTICIPATION_TR", "Ä°ÅŸgÃ¼cÃ¼ne KatÄ±lÄ±m OranÄ±", MacroFrequency.MONTHLY, MacroUnit.PERCENT)
                ),
                "last-last", "0-0", startDate, endDate, "0"
        );
    }

    public static MacroSeriesRequest consumerConfidence(String startDate, String endDate) {
        return new MacroSeriesRequest(
                "TP.TG2.Y01",
                List.of(
                        new MacroSeriesField("TP_TG2_Y01", MacroValueType.CONSUMER_CONFIDENCE_INDEX, "CONSUMER_CONFIDENCE_TR")
                ),
                List.of(new MacroIndicatorDef("CONSUMER_CONFIDENCE_TR", "TÃ¼ketici GÃ¼ven Endeksi", MacroFrequency.MONTHLY, MacroUnit.INDEX)),
                "last", "0", startDate, endDate, "0"
        );
    }

    public static MacroSeriesRequest currentAccount(String startDate, String endDate) {
        return new MacroSeriesRequest(
                "TP.ODEAYRSUNUM6.Q1",
                List.of(
                        new MacroSeriesField("TP_ODEAYRSUNUM6_Q1", MacroValueType.CURRENT_ACCOUNT_BALANCE, "CURRENT_ACCOUNT_TR")
                ),
                List.of(new MacroIndicatorDef("CURRENT_ACCOUNT_TR", "Cari Ä°ÅŸlemler HesabÄ±", MacroFrequency.MONTHLY, MacroUnit.USD)),
                "sum", "0", startDate, endDate, "0"
        );
    }

    private TcmbMacroSeries() {}
}





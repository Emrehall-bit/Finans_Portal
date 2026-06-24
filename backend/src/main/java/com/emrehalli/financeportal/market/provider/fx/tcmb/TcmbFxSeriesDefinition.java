package com.emrehalli.financeportal.market.provider.fx.tcmb;

public record TcmbFxSeriesDefinition(
        String instrumentCode,
        String seriesCode,
        String responseFieldName,
        String displayName
) {

    public static TcmbFxSeriesDefinition of(String instrumentCode, String seriesCode, String displayName) {
        return new TcmbFxSeriesDefinition(
                instrumentCode,
                seriesCode,
                toResponseFieldName(seriesCode),
                displayName
        );
    }

    public static String toResponseFieldName(String seriesCode) {
        return seriesCode == null ? null : seriesCode.replace('.', '_');
    }
}


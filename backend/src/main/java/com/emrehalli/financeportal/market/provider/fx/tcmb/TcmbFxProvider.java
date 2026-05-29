package com.emrehalli.financeportal.market.provider.fx.tcmb;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.MarketDataProvider;
import com.emrehalli.financeportal.market.provider.fx.dto.FxRateDto;
import com.emrehalli.financeportal.market.provider.fx.tcmb.client.TcmbEvdsClient;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbEvdsResponse;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbFxResponse;
import com.emrehalli.financeportal.market.provider.fx.tcmb.mapper.TcmbFxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
@Slf4j
public class TcmbFxProvider implements MarketDataProvider {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final List<String> SERIES_CODES = List.of(
            "TP.DK.EUR.A.YTL", "TP.DK.EUR.S.YTL",
            "TP.DK.USD.A.YTL", "TP.DK.USD.S.YTL",
            "TP.DK.AUD.A.YTL", "TP.DK.AUD.S.YTL",
            "TP.DK.AZN.A.YTL", "TP.DK.AZN.S.YTL",
            "TP.DK.CNY.A.YTL", "TP.DK.CNY.S.YTL",
            "TP.DK.KRW.A.YTL", "TP.DK.KRW.S.YTL",
            "TP.DK.GBP.A.YTL", "TP.DK.GBP.S.YTL",
            "TP.DK.JPY.A.YTL", "TP.DK.JPY.S.YTL",
            "TP.DK.KWD.A.YTL", "TP.DK.KWD.S.YTL",
            "TP.DK.QAR.A.YTL", "TP.DK.QAR.S.YTL",
            "TP.DK.RUB.A.YTL", "TP.DK.RUB.S.YTL",
            "TP.DK.CHF.A.YTL", "TP.DK.CHF.S.YTL",
            "TP.DK.CAD.A.YTL", "TP.DK.CAD.S.YTL"
    );

    private final TcmbEvdsClient tcmbEvdsClient;
    private final TcmbFxMapper tcmbFxMapper;
    private final MarketProperties marketProperties;

    public TcmbFxProvider(TcmbEvdsClient tcmbEvdsClient, TcmbFxMapper tcmbFxMapper, MarketProperties marketProperties) {
        this.tcmbEvdsClient = tcmbEvdsClient;
        this.tcmbFxMapper = tcmbFxMapper;
        this.marketProperties = marketProperties;
    }

    @Override
    public String getSourceName() {
        return SourceName.TCMB.name();
    }

    @Override
    public List<FxRateDto> fetch() {
        LocalDate startDate = LocalDate.now().minusDays(5);
        LocalDate endDate = resolveEndDate();
        String series = String.join("-", SERIES_CODES);

        TcmbEvdsResponse evdsResponse = tcmbEvdsClient.fetch(series, startDate, endDate);
        TcmbFxResponse fxResponse = tcmbFxMapper.toFxResponse(evdsResponse);
        List<FxRateDto> rates = tcmbFxMapper.toFxRates(fxResponse, SERIES_CODES);

        if (rates.isEmpty()) {
            log.warn("TCMB provider returned no parsable FX records. Weekend or holiday assumed.");
        }
        return rates;
    }

    private LocalDate resolveEndDate() {
        String configuredEndDate = marketProperties.getProviders().getTcmb().getEndDate();
        if (configuredEndDate == null || configuredEndDate.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(configuredEndDate, DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new DataProviderException("Failed to parse configured TCMB end date", exception);
        }
    }
}





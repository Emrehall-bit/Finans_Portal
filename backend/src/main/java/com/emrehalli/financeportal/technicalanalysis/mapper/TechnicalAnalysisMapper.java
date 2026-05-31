package com.emrehalli.financeportal.technicalanalysis.mapper;

import com.emrehalli.financeportal.technicalanalysis.dto.ComparisonResponse;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResponse;
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.service.model.ComparisonResult;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class TechnicalAnalysisMapper {

    public TechnicalAnalysisResponse toResponse(TechnicalAnalysisResult result) {
        return new TechnicalAnalysisResponse(
                result.symbol(),
                result.from(),
                result.to(),
                result.latestPrice(),
                result.analysisStatus(),
                result.message(),
                result.trendDirection().name(),
                result.signals().stream().map(Enum::name).toList(),
                toIndicatorDtos(result.indicatorValues()),
                result.points().stream().map(this::toPointDto).toList()
        );
    }

    public ComparisonResponse toResponse(ComparisonResult result) {
        return new ComparisonResponse(
                result.from(),
                result.to(),
                result.series().stream().map(this::toSeriesDto).toList()
        );
    }

    private List<TechnicalAnalysisResponse.IndicatorValue> toIndicatorDtos(Map<IndicatorType, BigDecimal> indicatorValues) {
        return Arrays.stream(IndicatorType.values())
                .filter(indicatorValues::containsKey)
                .map(indicatorType -> new TechnicalAnalysisResponse.IndicatorValue(indicatorType.name(), indicatorValues.get(indicatorType)))
                .toList();
    }

    private TechnicalAnalysisResponse.Point toPointDto(TechnicalAnalysisResult.Point point) {
        return new TechnicalAnalysisResponse.Point(
                point.date(),
                point.close(),
                point.sma7(),
                point.sma20(),
                point.sma50(),
                point.rsi14()
        );
    }

    private ComparisonResponse.Series toSeriesDto(ComparisonResult.Series series) {
        return new ComparisonResponse.Series(
                series.symbol(),
                series.points().stream().map(this::toPointDto).toList()
        );
    }

    private ComparisonResponse.Point toPointDto(ComparisonResult.Point point) {
        return new ComparisonResponse.Point(
                point.date(),
                point.close(),
                point.normalizedValue()
        );
    }
}

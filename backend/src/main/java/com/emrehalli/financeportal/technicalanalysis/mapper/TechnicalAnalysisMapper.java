package com.emrehalli.financeportal.technicalanalysis.mapper;

import com.emrehalli.financeportal.technicalanalysis.dto.ComparisonPointDto;
import com.emrehalli.financeportal.technicalanalysis.dto.ComparisonResponse;
import com.emrehalli.financeportal.technicalanalysis.dto.ComparisonSeriesDto;
import com.emrehalli.financeportal.technicalanalysis.dto.IndicatorValueDto;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisPointDto;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResponse;
import com.emrehalli.financeportal.technicalanalysis.enums.IndicatorType;
import com.emrehalli.financeportal.technicalanalysis.service.model.ComparisonPoint;
import com.emrehalli.financeportal.technicalanalysis.service.model.ComparisonResult;
import com.emrehalli.financeportal.technicalanalysis.service.model.ComparisonSeries;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisPoint;
import com.emrehalli.financeportal.technicalanalysis.service.model.TechnicalAnalysisResult;
import org.springframework.stereotype.Component;

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

    private List<IndicatorValueDto> toIndicatorDtos(Map<IndicatorType, java.math.BigDecimal> indicatorValues) {
        return Arrays.stream(IndicatorType.values())
                .filter(indicatorValues::containsKey)
                .map(indicatorType -> new IndicatorValueDto(indicatorType.name(), indicatorValues.get(indicatorType)))
                .toList();
    }

    private TechnicalAnalysisPointDto toPointDto(TechnicalAnalysisPoint point) {
        return new TechnicalAnalysisPointDto(
                point.date(),
                point.close(),
                point.sma7(),
                point.sma20(),
                point.sma50(),
                point.rsi14()
        );
    }

    private ComparisonSeriesDto toSeriesDto(ComparisonSeries series) {
        return new ComparisonSeriesDto(
                series.symbol(),
                series.points().stream().map(this::toPointDto).toList()
        );
    }

    private ComparisonPointDto toPointDto(ComparisonPoint point) {
        return new ComparisonPointDto(
                point.date(),
                point.close(),
                point.normalizedValue()
        );
    }
}

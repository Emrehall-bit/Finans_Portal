package com.emrehalli.financeportal.technicalanalysis.controller;

import com.emrehalli.financeportal.technicalanalysis.dto.ComparisonResponse;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResponse;
import com.emrehalli.financeportal.technicalanalysis.mapper.TechnicalAnalysisMapper;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/technical-analysis")
public class TechnicalAnalysisController {

    private final TechnicalAnalysisService technicalAnalysisService;
    private final TechnicalAnalysisMapper technicalAnalysisMapper;

    public TechnicalAnalysisController(TechnicalAnalysisService technicalAnalysisService,
                                       TechnicalAnalysisMapper technicalAnalysisMapper) {
        this.technicalAnalysisService = technicalAnalysisService;
        this.technicalAnalysisMapper = technicalAnalysisMapper;
    }

    @GetMapping("/{symbol}")
    public TechnicalAnalysisResponse analyze(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String indicators
    ) {
        return technicalAnalysisMapper.toResponse(
                technicalAnalysisService.analyze(symbol, from, to, indicators)
        );
    }

    @GetMapping("/compare")
    public ComparisonResponse compare(
            @RequestParam String symbols,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return technicalAnalysisMapper.toResponse(
                technicalAnalysisService.compare(symbols, from, to)
        );
    }
}

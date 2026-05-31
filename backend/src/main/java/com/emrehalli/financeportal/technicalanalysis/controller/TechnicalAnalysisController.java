package com.emrehalli.financeportal.technicalanalysis.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.technicalanalysis.dto.ComparisonResponse;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalCandleDto;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResponse;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException;
import com.emrehalli.financeportal.technicalanalysis.mapper.TechnicalAnalysisMapper;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalCandleService;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Validated
@RestController
@RequestMapping("/api/v1/technical-analysis")
public class TechnicalAnalysisController {

    private static final String SYMBOL_PATTERN = "^[A-Za-z0-9._\\-:]+$";

    private final TechnicalAnalysisService technicalAnalysisService;
    private final TechnicalCandleService technicalCandleService;
    private final TechnicalAnalysisMapper technicalAnalysisMapper;

    public TechnicalAnalysisController(TechnicalAnalysisService technicalAnalysisService,
                                       TechnicalCandleService technicalCandleService,
                                       TechnicalAnalysisMapper technicalAnalysisMapper) {
        this.technicalAnalysisService = technicalAnalysisService;
        this.technicalCandleService = technicalCandleService;
        this.technicalAnalysisMapper = technicalAnalysisMapper;
    }

    @GetMapping("/{symbol}")
    public TechnicalAnalysisResponse analyze(
            @PathVariable
            @Size(max = 30, message = "symbol too long: maximum 30 characters allowed")
            @Pattern(regexp = SYMBOL_PATTERN, message = "symbol contains invalid characters: only letters, digits, '.', '-', '_', ':' are allowed")
            String symbol,
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

    @GetMapping("/{symbol}/candles")
    public ResponseEntity<ApiResponse<List<TechnicalCandleDto>>> getCandles(@PathVariable
                                                                            @Size(max = 30, message = "symbol too long: maximum 30 characters allowed")
                                                                            @Pattern(regexp = SYMBOL_PATTERN, message = "symbol contains invalid characters: only letters, digits, '.', '-', '_', ':' are allowed")
                                                                            String symbol,
                                                                            @RequestParam(defaultValue = "6m") String range,
                                                                            @RequestParam(defaultValue = "1d") String interval) {
        try {
            List<TechnicalCandleDto> candles = technicalCandleService.getCandles(symbol, range, interval);
            return ResponseEntity.ok(ApiResponse.<List<TechnicalCandleDto>>builder()
                    .success(true)
                    .data(candles)
                    .message("Candlestick data loaded")
                    .build());
        } catch (TechnicalAnalysisException.NotFound ex) {
            return buildCandleError(symbol, HttpStatus.NOT_FOUND);
        } catch (TechnicalAnalysisException.Validation ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Candlestick data not available for symbol ")) {
                return buildCandleError(symbol, HttpStatus.BAD_REQUEST);
            }
            throw ex;
        }
    }

    private ResponseEntity<ApiResponse<List<TechnicalCandleDto>>> buildCandleError(String symbol, HttpStatus status) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        return ResponseEntity.status(status).body(ApiResponse.<List<TechnicalCandleDto>>builder()
                .success(false)
                .message("Candlestick data not available for symbol " + normalizedSymbol)
                .build());
    }
}





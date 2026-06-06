package com.emrehalli.financeportal.technicalanalysis.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.config.security.CurrentUser;
import com.emrehalli.financeportal.config.security.CurrentUserResolver;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.technicalanalysis.dto.IndicatorConfigDtos.Request;
import com.emrehalli.financeportal.technicalanalysis.dto.IndicatorConfigDtos.Response;
import com.emrehalli.financeportal.technicalanalysis.service.BenchmarkComparisonService;
import com.emrehalli.financeportal.technicalanalysis.service.IndicatorConfigService;
import com.emrehalli.financeportal.technicalanalysis.dto.BenchmarkResponse;
import com.emrehalli.financeportal.technicalanalysis.dto.ComparisonResponse;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalCandleDto;
import com.emrehalli.financeportal.technicalanalysis.dto.TechnicalAnalysisResponse;
import com.emrehalli.financeportal.technicalanalysis.exception.TechnicalAnalysisException;
import com.emrehalli.financeportal.technicalanalysis.mapper.TechnicalAnalysisMapper;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalCandleService;
import com.emrehalli.financeportal.technicalanalysis.service.TechnicalAnalysisService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private static final Logger logger = LogManager.getLogger(TechnicalAnalysisController.class);

    private final TechnicalAnalysisService technicalAnalysisService;
    private final TechnicalCandleService technicalCandleService;
    private final TechnicalAnalysisMapper technicalAnalysisMapper;
    private final IndicatorConfigService indicatorConfigService;
    private final CurrentUserResolver currentUserResolver;
    private final BenchmarkComparisonService benchmarkComparisonService;

    public TechnicalAnalysisController(TechnicalAnalysisService technicalAnalysisService,
                                       TechnicalCandleService technicalCandleService,
                                       TechnicalAnalysisMapper technicalAnalysisMapper,
                                       IndicatorConfigService indicatorConfigService,
                                       CurrentUserResolver currentUserResolver,
                                       BenchmarkComparisonService benchmarkComparisonService) {
        this.technicalAnalysisService = technicalAnalysisService;
        this.technicalCandleService = technicalCandleService;
        this.technicalAnalysisMapper = technicalAnalysisMapper;
        this.indicatorConfigService = indicatorConfigService;
        this.currentUserResolver = currentUserResolver;
        this.benchmarkComparisonService = benchmarkComparisonService;
    }

    @GetMapping("/{symbol}")
    public TechnicalAnalysisResponse analyze(
            @PathVariable
            @Size(max = 30, message = "symbol too long: maximum 30 characters allowed")
            @Pattern(regexp = SYMBOL_PATTERN, message = "symbol contains invalid characters: only letters, digits, '.', '-', '_', ':' are allowed")
            String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String indicators,
            @RequestParam(required = false) InstrumentType instrumentType
    ) {
        return technicalAnalysisMapper.toResponse(
                technicalAnalysisService.analyze(symbol, from, to, indicators, instrumentType)
        );
    }

    @GetMapping("/benchmark")
    public BenchmarkResponse benchmark(
            @RequestParam String baseCode,
            @RequestParam String benchmarkCode,
            @RequestParam String benchmarkType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return benchmarkComparisonService.compare(baseCode, benchmarkCode, benchmarkType, from, to);
    }

    @GetMapping("/compare")
    public ComparisonResponse compare(
            @RequestParam String symbols,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return technicalAnalysisService.compare(symbols, from, to);
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

    @GetMapping("/{instrumentCode}/indicators")
    public ApiResponse<List<Response>> getUserIndicators(@PathVariable String instrumentCode) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Gosterge konfigurasyonlari getiriliyor: keycloakId={}, instrument={}",
                currentUser.keycloakId(), instrumentCode);

        List<Response> configs = indicatorConfigService
                .getActiveIndicators(currentUser.keycloakId(), instrumentCode);

        return ApiResponse.<List<Response>>builder()
                .success(true)
                .data(configs)
                .message("Gosterge konfigurasyonlari getirildi")
                .build();
    }

    @PostMapping("/{instrumentCode}/indicators")
    public ApiResponse<Response> saveIndicatorConfig(@PathVariable String instrumentCode,
                                                     @Valid @RequestBody Request req) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Gosterge konfigurasyonu kaydediliyor: keycloakId={}, instrument={}, type={}",
                currentUser.keycloakId(), instrumentCode, req.getIndicatorType());

        Response saved = indicatorConfigService
                .saveIndicatorConfig(currentUser.keycloakId(), instrumentCode, req);

        return ApiResponse.<Response>builder()
                .success(true)
                .data(saved)
                .message("Gosterge konfigurasyonu kaydedildi")
                .build();
    }

    @DeleteMapping("/indicators/{id}")
    public ApiResponse<Void> deleteIndicatorConfig(@PathVariable Long id) {
        CurrentUser currentUser = currentUserResolver.resolve();
        logger.info("Gosterge konfigurasyonu siliniyor: keycloakId={}, configId={}", currentUser.keycloakId(), id);

        indicatorConfigService.deleteIndicatorConfig(currentUser.keycloakId(), id);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Gosterge konfigurasyonu silindi")
                .build();
    }
}



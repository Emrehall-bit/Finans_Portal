package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.market.service.MarketQueryService;
import com.emrehalli.financeportal.portfolio.dto.PortfolioPerformancePointDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioPerformanceResponse;
import com.emrehalli.financeportal.portfolio.entity.PortfolioHolding;
import com.emrehalli.financeportal.portfolio.repository.PortfolioHoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

@Service
public class PortfolioPerformanceHistoryService {

    private static final int DEFAULT_LOOKBACK_DAYS = 90;
    private static final String INTERNAL_CASH_CODE = "TRY";

    private final PortfolioService portfolioService;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final MarketQueryService marketQueryService;

    public PortfolioPerformanceHistoryService(PortfolioService portfolioService,
                                              PortfolioHoldingRepository portfolioHoldingRepository,
                                              MarketQueryService marketQueryService) {
        this.portfolioService = portfolioService;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.marketQueryService = marketQueryService;
    }

    @Transactional(readOnly = true)
    public PortfolioPerformanceResponse getPerformanceHistory(Long portfolioId, LocalDate from, LocalDate to) {
        portfolioService.getPortfolioEntityById(portfolioId);

        List<PortfolioHolding> holdings = portfolioHoldingRepository.findByPortfolioId(portfolioId);
        if (holdings.isEmpty()) {
            return new PortfolioPerformanceResponse(List.of(), null, null, true);
        }

        LocalDate resolvedTo = to != null ? to : LocalDate.now();
        LocalDate firstHoldingDate = holdings.stream()
                .map(this::resolveHoldingStartDate)
                .min(LocalDate::compareTo)
                .orElse(resolvedTo);
        LocalDate requestedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_LOOKBACK_DAYS - 1L);
        LocalDate resolvedFrom = requestedFrom.isAfter(firstHoldingDate) ? requestedFrom : firstHoldingDate;

        if (resolvedFrom.isAfter(resolvedTo)) {
            return new PortfolioPerformanceResponse(List.of(), resolvedFrom, resolvedTo, true);
        }

        List<HoldingHistoryContext> contexts = holdings.stream()
                .map(holding -> buildContext(holding, resolvedFrom, resolvedTo))
                .toList();

        List<PortfolioPerformancePointDto> points = new ArrayList<>();
        for (LocalDate date = resolvedFrom; !date.isAfter(resolvedTo); date = date.plusDays(1)) {
            BigDecimal totalValue = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;
            int activeHoldingCount = 0;

            for (HoldingHistoryContext context : contexts) {
                if (date.isBefore(context.startDate())) {
                    continue;
                }

                activeHoldingCount++;
                BigDecimal positionCost = context.buyPrice().multiply(context.quantity());
                totalCost = totalCost.add(positionCost);

                BigDecimal historicalPrice = resolvePriceAt(context, date);
                if (historicalPrice != null) {
                    totalValue = totalValue.add(historicalPrice.multiply(context.quantity()));
                }
            }

            if (activeHoldingCount == 0) {
                continue;
            }

            BigDecimal profitLoss = totalValue.subtract(totalCost);
            BigDecimal profitLossPercent = null;
            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                profitLossPercent = profitLoss
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalCost, 4, RoundingMode.HALF_UP);
            }

            points.add(new PortfolioPerformancePointDto(
                    date,
                    totalValue,
                    totalCost,
                    profitLoss,
                    profitLossPercent
            ));
        }

        return new PortfolioPerformanceResponse(points, resolvedFrom, resolvedTo, true);
    }

    private HoldingHistoryContext buildContext(PortfolioHolding holding, LocalDate from, LocalDate to) {
        LocalDate startDate = resolveHoldingStartDate(holding);
        NavigableMap<LocalDate, BigDecimal> historicalPrices = new TreeMap<>();

        if (!INTERNAL_CASH_CODE.equalsIgnoreCase(holding.getInstrumentCode())) {
            LocalDate historyFrom = startDate.isAfter(from) ? startDate : from;
            marketQueryService.getHistory(holding.getInstrumentCode(), null, historyFrom, to).forEach(point -> {
                if (point.priceDate() != null && point.closePrice() != null) {
                    historicalPrices.put(point.priceDate(), point.closePrice());
                }
            });
        }

        return new HoldingHistoryContext(
                holding.getInstrumentCode(),
                startDate,
                holding.getQuantity(),
                holding.getBuyPrice(),
                historicalPrices
        );
    }

    private BigDecimal resolvePriceAt(HoldingHistoryContext context, LocalDate date) {
        if (INTERNAL_CASH_CODE.equalsIgnoreCase(context.instrumentCode())) {
            return BigDecimal.ONE;
        }

        var exactOrPreviousPrice = context.historicalPrices().floorEntry(date);
        if (exactOrPreviousPrice != null && exactOrPreviousPrice.getValue() != null) {
            return exactOrPreviousPrice.getValue();
        }

        return context.buyPrice().compareTo(BigDecimal.ZERO) > 0 ? context.buyPrice() : null;
    }

    private LocalDate resolveHoldingStartDate(PortfolioHolding holding) {
        if (holding.getPurchaseDate() != null) {
            return holding.getPurchaseDate();
        }
        if (holding.getCreatedAt() != null) {
            return holding.getCreatedAt().toLocalDate();
        }
        if (holding.getUpdatedAt() != null) {
            return holding.getUpdatedAt().toLocalDate();
        }
        return LocalDate.now();
    }

    private record HoldingHistoryContext(
            String instrumentCode,
            LocalDate startDate,
            BigDecimal quantity,
            BigDecimal buyPrice,
            NavigableMap<LocalDate, BigDecimal> historicalPrices
    ) {
    }
}

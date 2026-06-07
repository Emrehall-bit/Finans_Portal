package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.portfolio.dto.CreatePortfolioHoldingRequest;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.dto.UpdatePortfolioHoldingRequest;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import com.emrehalli.financeportal.portfolio.entity.PortfolioHolding;
import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import com.emrehalli.financeportal.portfolio.enums.SummaryStatus;
import com.emrehalli.financeportal.portfolio.repository.PortfolioHoldingRepository;
import com.emrehalli.financeportal.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioHoldingService {

    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPriceResolver portfolioPriceResolver;
    private final MarketInstrumentRepository marketInstrumentRepository;

    public PortfolioHoldingService(PortfolioHoldingRepository portfolioHoldingRepository,
                                   PortfolioRepository portfolioRepository,
                                   PortfolioPriceResolver portfolioPriceResolver,
                                   MarketInstrumentRepository marketInstrumentRepository) {
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.portfolioRepository = portfolioRepository;
        this.portfolioPriceResolver = portfolioPriceResolver;
        this.marketInstrumentRepository = marketInstrumentRepository;
    }

    @Transactional
    public PortfolioHoldingDto createHolding(Long portfolioId, CreatePortfolioHoldingRequest request) {
        Portfolio portfolio = findPortfolio(portfolioId);
        String instrumentCode = normalizeInstrumentCode(request.getInstrumentCode());

        if (!marketInstrumentRepository.existsByInstrumentCodeIgnoreCase(instrumentCode)) {
            throw new BadRequestException("Sistemde kayÄ±tlÄ± olmayan enstrÃ¼man: " + instrumentCode);
        }

        LocalDateTime now = LocalDateTime.now();
        PortfolioHolding holding = PortfolioHolding.builder()
                .portfolio(portfolio)
                .instrumentCode(instrumentCode)
                .quantity(request.getQuantity())
                .buyPrice(request.getBuyPrice())
                .purchaseDate(request.getPurchaseDate())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toDto(portfolioHoldingRepository.save(holding));
    }

    @Transactional
    public PortfolioHoldingDto updateHolding(Long portfolioId, Long holdingId, UpdatePortfolioHoldingRequest request) {
        PortfolioHolding holding = getHoldingEntity(portfolioId, holdingId);
        holding.setQuantity(request.getQuantity());
        holding.setBuyPrice(request.getBuyPrice());
        if (request.getPurchaseDate() != null) {
            holding.setPurchaseDate(request.getPurchaseDate());
        }
        holding.setUpdatedAt(LocalDateTime.now());
        return toDto(portfolioHoldingRepository.save(holding));
    }

    @Transactional
    public void deleteHolding(Long portfolioId, Long holdingId) {
        PortfolioHolding holding = getHoldingEntity(portfolioId, holdingId);
        portfolioHoldingRepository.delete(holding);
    }

    @Transactional(readOnly = true)
    public List<PortfolioHoldingDto> getHoldingsByPortfolioId(Long portfolioId) {
        ensurePortfolioExists(portfolioId);
        return portfolioHoldingRepository.findByPortfolioId(portfolioId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getPortfolioSummary(Long portfolioId) {
        return getPortfolioValuation(portfolioId).summary();
    }

    @Transactional(readOnly = true)
    public PortfolioValuationResult getPortfolioValuation(Long portfolioId) {
        List<PortfolioHoldingDto> rawHoldings = getHoldingsByPortfolioId(portfolioId);
        List<PortfolioHoldingDto> holdings = aggregateByInstrument(rawHoldings);
        return new PortfolioValuationResult(holdings, buildSummary(holdings));
    }

    private List<PortfolioHoldingDto> aggregateByInstrument(List<PortfolioHoldingDto> rawHoldings) {
        Map<String, List<PortfolioHoldingDto>> grouped = new LinkedHashMap<>();
        for (PortfolioHoldingDto holding : rawHoldings) {
            String key = holding.getInstrumentCode() == null ? "" : holding.getInstrumentCode().trim().toUpperCase();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(holding);
        }

        List<PortfolioHoldingDto> result = new ArrayList<>(grouped.size());
        for (List<PortfolioHoldingDto> group : grouped.values()) {
            result.add(group.size() == 1 ? wrapSingle(group.get(0)) : mergeGroup(group));
        }
        return result;
    }

    private PortfolioHoldingDto wrapSingle(PortfolioHoldingDto holding) {
        return PortfolioHoldingDto.builder()
                .holdingId(holding.getHoldingId())
                .instrumentCode(holding.getInstrumentCode())
                .quantity(holding.getQuantity())
                .buyPrice(holding.getBuyPrice())
                .currentPrice(holding.getCurrentPrice())
                .currentValue(holding.getCurrentValue())
                .dailyProfitLoss(holding.getDailyProfitLoss())
                .dailyChangePercent(holding.getDailyChangePercent())
                .profitLoss(holding.getProfitLoss())
                .profitLossPercent(holding.getProfitLossPercent())
                .priceStatus(holding.getPriceStatus())
                .lastPriceUpdateTime(holding.getLastPriceUpdateTime())
                .valuationAvailable(holding.isValuationAvailable())
                .purchaseDate(holding.getPurchaseDate())
                .createdAt(holding.getCreatedAt())
                .updatedAt(holding.getUpdatedAt())
                .entryCount(1)
                .sourceHoldingIds(List.of(holding.getHoldingId()))
                .build();
    }

    private PortfolioHoldingDto mergeGroup(List<PortfolioHoldingDto> group) {
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        BigDecimal totalDailyProfitLoss = BigDecimal.ZERO;
        BigDecimal totalDailyChangePercent = BigDecimal.ZERO;
        boolean anyValuationAvailable = false;
        boolean anyDailyPnl = false;
        PriceStatus bestPriceStatus = PriceStatus.UNAVAILABLE;
        LocalDateTime latestPriceUpdateTime = null;
        LocalDateTime earliestCreatedAt = null;
        LocalDateTime latestUpdatedAt = null;
        LocalDate earliestPurchaseDate = null;
        List<Long> sourceIds = new ArrayList<>(group.size());

        for (PortfolioHoldingDto h : group) {
            BigDecimal qty = zeroIfNull(h.getQuantity());
            totalQuantity = totalQuantity.add(qty);
            totalCost = totalCost.add(qty.multiply(zeroIfNull(h.getBuyPrice())));
            sourceIds.add(h.getHoldingId());

            if (h.isValuationAvailable() && h.getCurrentValue() != null) {
                anyValuationAvailable = true;
                totalCurrentValue = totalCurrentValue.add(h.getCurrentValue());
            }
            if (h.getDailyProfitLoss() != null) {
                anyDailyPnl = true;
                totalDailyProfitLoss = totalDailyProfitLoss.add(h.getDailyProfitLoss());
            }
            if (h.getDailyChangePercent() != null) {
                totalDailyChangePercent = totalDailyChangePercent.add(h.getDailyChangePercent());
            }
            if (h.getPriceStatus() == PriceStatus.LIVE) {
                bestPriceStatus = PriceStatus.LIVE;
            } else if (h.getPriceStatus() == PriceStatus.STALE && bestPriceStatus == PriceStatus.UNAVAILABLE) {
                bestPriceStatus = PriceStatus.STALE;
            }
            if (h.getLastPriceUpdateTime() != null && (latestPriceUpdateTime == null || h.getLastPriceUpdateTime().isAfter(latestPriceUpdateTime))) {
                latestPriceUpdateTime = h.getLastPriceUpdateTime();
            }
            if (h.getCreatedAt() != null && (earliestCreatedAt == null || h.getCreatedAt().isBefore(earliestCreatedAt))) {
                earliestCreatedAt = h.getCreatedAt();
            }
            if (h.getUpdatedAt() != null && (latestUpdatedAt == null || h.getUpdatedAt().isAfter(latestUpdatedAt))) {
                latestUpdatedAt = h.getUpdatedAt();
            }
            if (h.getPurchaseDate() != null && (earliestPurchaseDate == null || h.getPurchaseDate().isBefore(earliestPurchaseDate))) {
                earliestPurchaseDate = h.getPurchaseDate();
            }
        }

        PortfolioHoldingDto first = group.get(0);
        BigDecimal avgBuyPrice = totalQuantity.compareTo(BigDecimal.ZERO) > 0
                ? totalCost.divide(totalQuantity, 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal currentPrice = anyValuationAvailable && totalQuantity.compareTo(BigDecimal.ZERO) > 0
                ? totalCurrentValue.divide(totalQuantity, 8, RoundingMode.HALF_UP)
                : null;
        BigDecimal profitLoss = anyValuationAvailable ? totalCurrentValue.subtract(totalCost) : null;
        BigDecimal profitLossPercent = null;
        if (profitLoss != null && totalCost.compareTo(BigDecimal.ZERO) > 0) {
            profitLossPercent = profitLoss.multiply(BigDecimal.valueOf(100))
                    .divide(totalCost, 4, RoundingMode.HALF_UP);
        }

        return PortfolioHoldingDto.builder()
                .holdingId(first.getHoldingId())
                .instrumentCode(first.getInstrumentCode())
                .quantity(totalQuantity)
                .buyPrice(avgBuyPrice)
                .currentPrice(currentPrice)
                .currentValue(anyValuationAvailable ? totalCurrentValue : null)
                .dailyProfitLoss(anyDailyPnl ? totalDailyProfitLoss : null)
                .dailyChangePercent(anyDailyPnl ? totalDailyChangePercent : null)
                .profitLoss(profitLoss)
                .profitLossPercent(profitLossPercent)
                .priceStatus(bestPriceStatus)
                .lastPriceUpdateTime(latestPriceUpdateTime)
                .valuationAvailable(anyValuationAvailable)
                .purchaseDate(earliestPurchaseDate)
                .createdAt(earliestCreatedAt)
                .updatedAt(latestUpdatedAt)
                .entryCount(group.size())
                .sourceHoldingIds(sourceIds)
                .build();
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private PortfolioSummaryResponse buildSummary(List<PortfolioHoldingDto> holdings) {
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;
        BigDecimal dailyProfitLoss = BigDecimal.ZERO;
        BigDecimal profitLoss = BigDecimal.ZERO;
        int missingPriceCount = 0;
        int valuedHoldingCount = 0;
        int dailyValuedHoldingCount = 0;

        for (PortfolioHoldingDto holding : holdings) {
            totalCost = totalCost.add(holding.getBuyPrice().multiply(holding.getQuantity()));
            if (!holding.isValuationAvailable()) {
                missingPriceCount++;
                continue;
            }

            currentValue = currentValue.add(holding.getCurrentValue());
            profitLoss = profitLoss.add(holding.getProfitLoss());
            valuedHoldingCount++;
            if (holding.getDailyProfitLoss() != null) {
                dailyProfitLoss = dailyProfitLoss.add(holding.getDailyProfitLoss());
                dailyValuedHoldingCount++;
            }
        }

        BigDecimal summaryCurrentValue = valuedHoldingCount > 0 ? currentValue : null;
        BigDecimal summaryDailyProfitLoss = dailyValuedHoldingCount > 0 ? dailyProfitLoss : null;
        BigDecimal summaryProfitLoss = valuedHoldingCount > 0 ? profitLoss : null;
        BigDecimal dailyProfitLossPercent = null;
        BigDecimal profitLossPercent = null;
        BigDecimal costBasisForValuedHoldings = holdings.stream()
                .filter(PortfolioHoldingDto::isValuationAvailable)
                .map(holding -> holding.getBuyPrice().multiply(holding.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (valuedHoldingCount > 0 && costBasisForValuedHoldings.compareTo(BigDecimal.ZERO) > 0) {
            profitLossPercent = profitLoss
                    .multiply(BigDecimal.valueOf(100))
                    .divide(costBasisForValuedHoldings, 4, RoundingMode.HALF_UP);
        }
        if (dailyValuedHoldingCount > 0 && summaryCurrentValue != null && summaryDailyProfitLoss != null) {
            BigDecimal previousValue = summaryCurrentValue.subtract(summaryDailyProfitLoss);
            if (previousValue.compareTo(BigDecimal.ZERO) > 0) {
                dailyProfitLossPercent = summaryDailyProfitLoss
                        .multiply(BigDecimal.valueOf(100))
                        .divide(previousValue, 4, RoundingMode.HALF_UP);
            }
        }

        SummaryStatus summaryStatus = resolveSummaryStatus(holdings.size(), valuedHoldingCount);

        return new PortfolioSummaryResponse(
                totalCost,
                summaryCurrentValue,
                summaryDailyProfitLoss,
                dailyProfitLossPercent,
                summaryProfitLoss,
                profitLossPercent,
                summaryStatus,
                missingPriceCount
        );
    }

    // Converts DB-backed holdings into best-effort valuations without failing the whole response.
    private PortfolioHoldingDto toDto(PortfolioHolding holding) {
        LocalDateTime priceRef = holding.getUpdatedAt() != null ? holding.getUpdatedAt() : holding.getCreatedAt();
        PriceResolutionResult priceResolution = portfolioPriceResolver
                .resolveCurrentPriceWithFallback(
                        holding.getInstrumentCode(),
                        holding.getBuyPrice(),
                        priceRef);

        BigDecimal currentValue = null;
        BigDecimal dailyProfitLoss = null;
        BigDecimal dailyChangePercent = null;
        BigDecimal profitLoss = null;
        BigDecimal profitLossPercent = null;

        if (priceResolution.valuationAvailable()) {
            BigDecimal totalCost = holding.getBuyPrice().multiply(holding.getQuantity());
            currentValue = priceResolution.price().multiply(holding.getQuantity());
            dailyChangePercent = priceResolution.changeRate();
            dailyProfitLoss = calculateDailyProfitLoss(priceResolution.price(), priceResolution.changeRate(), holding.getQuantity());
            profitLoss = currentValue.subtract(totalCost);

            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                profitLossPercent = profitLoss
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalCost, 4, RoundingMode.HALF_UP);
            }
        }

        return PortfolioHoldingDto.builder()
                .holdingId(holding.getId())
                .instrumentCode(holding.getInstrumentCode())
                .quantity(holding.getQuantity())
                .buyPrice(holding.getBuyPrice())
                .currentPrice(priceResolution.price())
                .currentValue(currentValue)
                .dailyProfitLoss(dailyProfitLoss)
                .dailyChangePercent(dailyChangePercent)
                .profitLoss(profitLoss)
                .profitLossPercent(profitLossPercent)
                .priceStatus(priceResolution.priceStatus())
                .lastPriceUpdateTime(priceResolution.lastPriceUpdateTime())
                .valuationAvailable(priceResolution.valuationAvailable())
                .purchaseDate(holding.getPurchaseDate())
                .createdAt(holding.getCreatedAt())
                .updatedAt(holding.getUpdatedAt())
                .build();
    }

    private Portfolio findPortfolio(Long portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with id: " + portfolioId));
    }

    private PortfolioHolding getHoldingEntity(Long portfolioId, Long holdingId) {
        ensurePortfolioExists(portfolioId);
        return portfolioHoldingRepository.findByIdAndPortfolioId(holdingId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Holding not found with id: " + holdingId));
    }

    private void ensurePortfolioExists(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found with id: " + portfolioId);
        }
    }

    private String normalizeInstrumentCode(String instrumentCode) {
        return instrumentCode == null ? null : instrumentCode.trim().toUpperCase();
    }

    // Summary status reflects whether all, some, or none of the holdings were valuated.
    private SummaryStatus resolveSummaryStatus(int holdingCount, int valuedHoldingCount) {
        if (holdingCount == 0) {
            return SummaryStatus.COMPLETE;
        }
        if (valuedHoldingCount == 0) {
            return SummaryStatus.UNAVAILABLE;
        }
        if (valuedHoldingCount == holdingCount) {
            return SummaryStatus.COMPLETE;
        }
        return SummaryStatus.PARTIAL;
    }

    private BigDecimal calculateDailyProfitLoss(BigDecimal currentPrice,
                                                BigDecimal changeRate,
                                                BigDecimal quantity) {
        if (currentPrice == null || changeRate == null || quantity == null) {
            return null;
        }

        BigDecimal divisor = BigDecimal.ONE.add(
                changeRate.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
        );
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal previousClose = currentPrice.divide(divisor, 8, RoundingMode.HALF_UP);
        return currentPrice.subtract(previousClose).multiply(quantity);
    }
}





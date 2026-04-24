package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.portfolio.dto.CreatePortfolioHoldingRequest;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.dto.UpdatePortfolioHoldingRequest;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import com.emrehalli.financeportal.portfolio.entity.PortfolioHolding;
import com.emrehalli.financeportal.portfolio.enums.SummaryStatus;
import com.emrehalli.financeportal.portfolio.repository.PortfolioHoldingRepository;
import com.emrehalli.financeportal.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PortfolioHoldingService {

    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPriceResolver portfolioPriceResolver;

    public PortfolioHoldingService(PortfolioHoldingRepository portfolioHoldingRepository,
                                   PortfolioRepository portfolioRepository,
                                   PortfolioPriceResolver portfolioPriceResolver) {
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.portfolioRepository = portfolioRepository;
        this.portfolioPriceResolver = portfolioPriceResolver;
    }

    public PortfolioHoldingDto createHolding(Long portfolioId, CreatePortfolioHoldingRequest request) {
        Portfolio portfolio = findPortfolio(portfolioId);
        String instrumentCode = normalizeInstrumentCode(request.getInstrumentCode());

        LocalDateTime now = LocalDateTime.now();
        PortfolioHolding holding = PortfolioHolding.builder()
                .portfolio(portfolio)
                .instrumentCode(instrumentCode)
                .quantity(request.getQuantity())
                .buyPrice(request.getBuyPrice())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toDto(portfolioHoldingRepository.save(holding));
    }

    public PortfolioHoldingDto updateHolding(Long portfolioId, Long holdingId, UpdatePortfolioHoldingRequest request) {
        PortfolioHolding holding = getHoldingEntity(portfolioId, holdingId);
        holding.setQuantity(request.getQuantity());
        holding.setBuyPrice(request.getBuyPrice());
        holding.setUpdatedAt(LocalDateTime.now());
        return toDto(portfolioHoldingRepository.save(holding));
    }

    public void deleteHolding(Long portfolioId, Long holdingId) {
        PortfolioHolding holding = getHoldingEntity(portfolioId, holdingId);
        portfolioHoldingRepository.delete(holding);
    }

    public List<PortfolioHoldingDto> getHoldingsByPortfolioId(Long portfolioId) {
        ensurePortfolioExists(portfolioId);
        return portfolioHoldingRepository.findByPortfolioId(portfolioId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public PortfolioSummaryResponse getPortfolioSummary(Long portfolioId) {
        List<PortfolioHoldingDto> holdings = getHoldingsByPortfolioId(portfolioId);

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;
        BigDecimal profitLoss = BigDecimal.ZERO;
        int missingPriceCount = 0;
        int valuedHoldingCount = 0;

        for (PortfolioHoldingDto holding : holdings) {
            totalCost = totalCost.add(holding.getBuyPrice().multiply(holding.getQuantity()));
            if (!holding.isValuationAvailable()) {
                missingPriceCount++;
                continue;
            }

            currentValue = currentValue.add(holding.getCurrentValue());
            profitLoss = profitLoss.add(holding.getProfitLoss());
            valuedHoldingCount++;
        }

        BigDecimal summaryCurrentValue = valuedHoldingCount > 0 ? currentValue : null;
        BigDecimal summaryProfitLoss = valuedHoldingCount > 0 ? profitLoss : null;
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

        SummaryStatus summaryStatus = resolveSummaryStatus(holdings.size(), valuedHoldingCount);

        return new PortfolioSummaryResponse(
                totalCost,
                summaryCurrentValue,
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
        BigDecimal profitLoss = null;
        BigDecimal profitLossPercent = null;

        if (priceResolution.valuationAvailable()) {
            BigDecimal totalCost = holding.getBuyPrice().multiply(holding.getQuantity());
            currentValue = priceResolution.price().multiply(holding.getQuantity());
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
                .profitLoss(profitLoss)
                .profitLossPercent(profitLossPercent)
                .priceStatus(priceResolution.priceStatus())
                .lastPriceUpdateTime(priceResolution.lastPriceUpdateTime())
                .valuationAvailable(priceResolution.valuationAvailable())
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
}




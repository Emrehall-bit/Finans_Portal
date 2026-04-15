package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.market.cache.MarketDataCacheService;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.portfolio.dto.CreatePortfolioTransactionRequest;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.dto.PortfolioTransactionResponseDto;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import com.emrehalli.financeportal.portfolio.entity.PortfolioTransaction;
import com.emrehalli.financeportal.portfolio.entity.TransactionType;
import com.emrehalli.financeportal.portfolio.mapper.PortfolioTransactionMapper;
import com.emrehalli.financeportal.portfolio.repository.PortfolioRepository;
import com.emrehalli.financeportal.portfolio.repository.PortfolioTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PortfolioTransactionService {

    private final PortfolioTransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioTransactionMapper transactionMapper;
    private final MarketDataCacheService marketDataCacheService;

    public PortfolioTransactionService(PortfolioTransactionRepository transactionRepository,
                                       PortfolioRepository portfolioRepository,
                                       PortfolioTransactionMapper transactionMapper,
                                       MarketDataCacheService marketDataCacheService) {
        this.transactionRepository = transactionRepository;
        this.portfolioRepository = portfolioRepository;
        this.transactionMapper = transactionMapper;
        this.marketDataCacheService = marketDataCacheService;
    }

    public PortfolioTransactionResponseDto createTransaction(Long portfolioId, CreatePortfolioTransactionRequest request) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));

        BigDecimal currentPrice = resolveCurrentPrice(request.getInstrumentCode());

        if (request.getTransactionType() == TransactionType.SELL) {
            BigDecimal availableQuantity = getAvailableQuantity(portfolioId, request.getInstrumentCode());
            if (availableQuantity.compareTo(request.getQuantity()) < 0) {
                throw new IllegalArgumentException("Insufficient quantity to sell");
            }
        }

        PortfolioTransaction transaction = PortfolioTransaction.builder()
                .portfolio(portfolio)
                .instrumentCode(request.getInstrumentCode())
                .transactionType(request.getTransactionType())
                .quantity(request.getQuantity())
                .price(currentPrice)
                .transactionTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        return transactionMapper.toDto(transactionRepository.save(transaction));
    }

    public List<PortfolioTransactionResponseDto> getTransactionsByPortfolioId(Long portfolioId) {
        ensurePortfolioExists(portfolioId);
        return transactionRepository.findByPortfolioIdOrderByTransactionTimeDesc(portfolioId)
                .stream()
                .map(transactionMapper::toDto)
                .toList();
    }

    public List<PortfolioHoldingDto> getHoldingsByPortfolioId(Long portfolioId) {
        ensurePortfolioExists(portfolioId);
        List<PortfolioTransaction> transactions = transactionRepository.findByPortfolioId(portfolioId);

        Map<String, BigDecimal> netQtyMap = new HashMap<>();
        Map<String, BigDecimal> buyCostMap = new HashMap<>();
        Map<String, BigDecimal> buyQtyMap = new HashMap<>();

        for (PortfolioTransaction tx : transactions) {
            String code = tx.getInstrumentCode();

            netQtyMap.putIfAbsent(code, BigDecimal.ZERO);
            buyCostMap.putIfAbsent(code, BigDecimal.ZERO);
            buyQtyMap.putIfAbsent(code, BigDecimal.ZERO);

            if (tx.getTransactionType() == TransactionType.BUY) {
                netQtyMap.put(code, netQtyMap.get(code).add(tx.getQuantity()));
                buyQtyMap.put(code, buyQtyMap.get(code).add(tx.getQuantity()));
                buyCostMap.put(code, buyCostMap.get(code).add(tx.getPrice().multiply(tx.getQuantity())));
            } else {
                netQtyMap.put(code, netQtyMap.get(code).subtract(tx.getQuantity()));
            }
        }

        List<PortfolioHoldingDto> holdings = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : netQtyMap.entrySet()) {
            String code = entry.getKey();
            BigDecimal netQty = entry.getValue();

            if (netQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal avgBuyPrice = BigDecimal.ZERO;
            if (buyQtyMap.get(code).compareTo(BigDecimal.ZERO) > 0) {
                avgBuyPrice = buyCostMap.get(code)
                        .divide(buyQtyMap.get(code), 4, RoundingMode.HALF_UP);
            }

            BigDecimal currentPrice = resolveCurrentPrice(code);
            BigDecimal currentValue = currentPrice.multiply(netQty);
            BigDecimal costBasis = avgBuyPrice.multiply(netQty);
            BigDecimal profitLoss = currentValue.subtract(costBasis);
            BigDecimal profitLossPercent = BigDecimal.ZERO;
            if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
                profitLossPercent = profitLoss
                        .multiply(BigDecimal.valueOf(100))
                        .divide(costBasis, 4, RoundingMode.HALF_UP);
            }

            holdings.add(new PortfolioHoldingDto(
                    code,
                    netQty,
                    avgBuyPrice,
                    currentPrice,
                    currentValue,
                    profitLoss,
                    profitLossPercent
            ));
        }

        return holdings;
    }

    public PortfolioSummaryResponse getPortfolioSummary(Long portfolioId) {
        List<PortfolioHoldingDto> holdings = getHoldingsByPortfolioId(portfolioId);

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;
        BigDecimal profitLoss = BigDecimal.ZERO;

        for (PortfolioHoldingDto holding : holdings) {
            totalCost = totalCost.add(holding.getAverageBuyPrice().multiply(holding.getQuantity()));
            currentValue = currentValue.add(holding.getCurrentValue());
            profitLoss = profitLoss.add(holding.getProfitLoss());
        }

        BigDecimal profitLossPercent = BigDecimal.ZERO;
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            profitLossPercent = profitLoss
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalCost, 4, RoundingMode.HALF_UP);
        }

        return new PortfolioSummaryResponse(totalCost, currentValue, profitLoss, profitLossPercent);
    }

    private BigDecimal getAvailableQuantity(Long portfolioId, String instrumentCode) {
        List<PortfolioTransaction> transactions = transactionRepository.findByPortfolioId(portfolioId);

        BigDecimal result = BigDecimal.ZERO;
        for (PortfolioTransaction tx : transactions) {
            if (!instrumentCode.equalsIgnoreCase(tx.getInstrumentCode())) {
                continue;
            }

            if (tx.getTransactionType() == TransactionType.BUY) {
                result = result.add(tx.getQuantity());
            } else {
                result = result.subtract(tx.getQuantity());
            }
        }
        return result;
    }

    private BigDecimal resolveCurrentPrice(String instrumentCode) {
        List<MarketDataDto> marketData = marketDataCacheService.getTcmbData();

        return marketData.stream()
                .filter(item -> item.getSymbol() != null && item.getSymbol().equalsIgnoreCase(instrumentCode))
                .findFirst()
                .map(item -> new BigDecimal(item.getPrice())) // String'i BigDecimal'a çeviren kısım
                .orElseThrow(() -> new ResourceNotFoundException("Current price not found for: " + instrumentCode));
    }

    private void ensurePortfolioExists(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new ResourceNotFoundException("Portfolio not found with id: " + portfolioId);
        }
    }
}
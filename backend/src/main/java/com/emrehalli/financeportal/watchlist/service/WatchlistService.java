package com.emrehalli.financeportal.watchlist.service;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.DuplicateResourceException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.market.cache.MarketDataCacheService;
import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.repository.UserRepository;
import com.emrehalli.financeportal.watchlist.dto.WatchlistResponseDto;
import com.emrehalli.financeportal.watchlist.entity.Watchlist;
import com.emrehalli.financeportal.watchlist.repository.WatchlistRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final UserRepository userRepository;
    private final MarketDataCacheService marketDataCacheService;

    public WatchlistService(WatchlistRepository watchlistRepository,
                            UserRepository userRepository,
                            MarketDataCacheService marketDataCacheService) {
        this.watchlistRepository = watchlistRepository;
        this.userRepository = userRepository;
        this.marketDataCacheService = marketDataCacheService;
    }

    public WatchlistResponseDto addFavorite(Long userId, String instrumentCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String normalizedCode = normalizeSymbol(instrumentCode);

        if (watchlistRepository.existsByUserIdAndInstrumentCodeIgnoreCase(userId, normalizedCode)) {
            throw new DuplicateResourceException("Instrument already exists in watchlist: " + normalizedCode);
        }

        Watchlist watchlist = Watchlist.builder()
                .user(user)
                .instrumentCode(normalizedCode)
                .createdAt(LocalDateTime.now())
                .build();

        Watchlist saved = watchlistRepository.save(watchlist);
        return toResponse(saved);
    }

    public void removeFavorite(Long watchlistId) {
        Watchlist watchlist = watchlistRepository.findById(watchlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist item not found with id: " + watchlistId));
        watchlistRepository.delete(watchlist);
    }

    public List<WatchlistResponseDto> getUserWatchlist(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        return watchlistRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private WatchlistResponseDto toResponse(Watchlist watchlist) {
        MarketDataDto marketData = marketDataCacheService.findBySymbol(watchlist.getInstrumentCode());
        BigDecimal currentPrice = parsePrice(marketData);

        return WatchlistResponseDto.builder()
                .id(watchlist.getId())
                .userId(watchlist.getUser().getId())
                .instrumentCode(watchlist.getInstrumentCode())
                .createdAt(watchlist.getCreatedAt())
                .currentPrice(currentPrice)
                .source(marketData != null ? marketData.getSource() : null)
                .lastUpdated(marketData != null ? marketData.getLastUpdated() : null)
                .build();
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new BadRequestException("instrumentCode cannot be blank");
        }
        return symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private BigDecimal parsePrice(MarketDataDto marketData) {
        if (marketData == null || marketData.getPrice() == null || marketData.getPrice().isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(marketData.getPrice());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

package com.emrehalli.financeportal.watchlist.service;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.DuplicateResourceException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.repository.UserRepository;
import com.emrehalli.financeportal.watchlist.dto.WatchlistResponseDto;
import com.emrehalli.financeportal.watchlist.entity.Watchlist;
import com.emrehalli.financeportal.watchlist.repository.WatchlistRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final UserRepository userRepository;

    public WatchlistService(WatchlistRepository watchlistRepository,
                            UserRepository userRepository) {
        this.watchlistRepository = watchlistRepository;
        this.userRepository = userRepository;
    }

    public WatchlistResponseDto addFavorite(Long userId, String instrumentCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String normalizedCode = normalizeSymbol(instrumentCode);
        String strippedCode = normalizedCode.replaceAll("[^A-Z0-9]", "");

        boolean existsExact = watchlistRepository.existsByUserIdAndInstrumentCodeIgnoreCase(userId, normalizedCode);
        boolean existsPacked = !strippedCode.equals(normalizedCode)
                && watchlistRepository.existsByUserIdAndInstrumentCodeIgnoreCase(userId, strippedCode);

        if (existsExact || existsPacked) {
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
        return WatchlistResponseDto.builder()
                .id(watchlist.getId())
                .userId(watchlist.getUser().getId())
                .instrumentCode(watchlist.getInstrumentCode())
                .createdAt(watchlist.getCreatedAt())
                .currentPrice(null)
                .source(null)
                .lastUpdated(null)
                .build();
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new BadRequestException("instrumentCode cannot be blank");
        }
        String trimmed = symbol.trim().toUpperCase();
        // Preserve FX provider symbols (e.g. "TCMB:AUD:SELL") — stripping colons corrupts the format
        if (trimmed.matches("^[A-Z_]+:[A-Z]{2,4}:(BUY|SELL)$")) {
            return trimmed;
        }
        return trimmed.replaceAll("[^A-Z0-9]", "");
    }
}


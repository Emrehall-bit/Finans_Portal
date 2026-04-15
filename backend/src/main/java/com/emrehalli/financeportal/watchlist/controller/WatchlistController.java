package com.emrehalli.financeportal.watchlist.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.watchlist.dto.AddWatchlistRequest;
import com.emrehalli.financeportal.watchlist.dto.WatchlistResponseDto;
import com.emrehalli.financeportal.watchlist.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @PostMapping("/{userId}")
    public ApiResponse<WatchlistResponseDto> addFavorite(@PathVariable Long userId,
                                                         @Valid @RequestBody AddWatchlistRequest request) {
        WatchlistResponseDto response = watchlistService.addFavorite(userId, request.getInstrumentCode());

        return ApiResponse.<WatchlistResponseDto>builder()
                .success(true)
                .data(response)
                .message("Watchlist item added successfully")
                .build();
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<WatchlistResponseDto>> getUserWatchlist(@PathVariable Long userId) {
        List<WatchlistResponseDto> response = watchlistService.getUserWatchlist(userId);

        return ApiResponse.<List<WatchlistResponseDto>>builder()
                .success(true)
                .data(response)
                .message("User watchlist fetched successfully")
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> removeFavorite(@PathVariable Long id) {
        watchlistService.removeFavorite(id);

        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message("Watchlist item deleted successfully")
                .build();
    }
}

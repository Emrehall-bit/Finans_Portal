package com.emrehalli.financeportal.watchlist.controller;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.watchlist.dto.AddWatchlistRequest;
import com.emrehalli.financeportal.watchlist.dto.WatchlistResponseDto;
import com.emrehalli.financeportal.watchlist.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlist")
@Tag(name = "Takip Listesi", description = "Kullanıcı takip listesi (watchlist) işlemleri")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final AppMessageSource appMessageSource;

    public WatchlistController(WatchlistService watchlistService, AppMessageSource appMessageSource) {
        this.watchlistService = watchlistService;
        this.appMessageSource = appMessageSource;
    }

    @Operation(summary = "Takip listesine enstrüman ekle", description = "Belirtilen kullanıcının takip listesine yeni bir finansal enstrüman ekler")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Enstrüman başarıyla eklendi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Geçersiz istek verisi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    @PostMapping("/{userId}")
    public ApiResponse<WatchlistResponseDto> addFavorite(@PathVariable Long userId,
                                                         @Valid @RequestBody AddWatchlistRequest request) {
        WatchlistResponseDto response = watchlistService.addFavorite(userId, request.getInstrumentCode());

        return ApiResponse.<WatchlistResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("watchlist.added"))
                .build();
    }

    @Operation(summary = "Kullanıcının takip listesini getir", description = "Belirtilen kullanıcıya ait tüm takip listesi kayıtlarını getirir")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Takip listesi başarıyla getirildi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    @GetMapping("/user/{userId}")
    public ApiResponse<List<WatchlistResponseDto>> getUserWatchlist(@PathVariable Long userId) {
        List<WatchlistResponseDto> response = watchlistService.getUserWatchlist(userId);

        return ApiResponse.<List<WatchlistResponseDto>>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("watchlist.list.fetched"))
                .build();
    }

    @Operation(summary = "Takip listesinden enstrüman çıkar", description = "Belirtilen takip listesi kaydını kalıcı olarak siler")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Enstrüman başarıyla çıkarıldı"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Takip listesi kaydı bulunamadı")
    })
    @DeleteMapping("/{id}")
    public ApiResponse<Void> removeFavorite(@PathVariable Long id) {
        watchlistService.removeFavorite(id);

        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message(appMessageSource.get("watchlist.deleted"))
                .build();
    }
}


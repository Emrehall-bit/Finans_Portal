package com.emrehalli.financeportal.watchlist.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class WatchlistResponseDto {

    private Long id;
    private Long userId;
    private String instrumentCode;
    private LocalDateTime createdAt;
    private BigDecimal currentPrice;
    private String source;
    private String lastUpdated;
}

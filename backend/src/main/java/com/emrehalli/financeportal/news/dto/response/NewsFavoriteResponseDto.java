package com.emrehalli.financeportal.news.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NewsFavoriteResponseDto {
    private Long id;
    private Long userId;
    private Long newsId;
    private LocalDateTime createdAt;
}

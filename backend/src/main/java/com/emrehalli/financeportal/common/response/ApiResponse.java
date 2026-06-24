package com.emrehalli.financeportal.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Tüm API endpoint'lerinin standart yanıt zarfı")
public class ApiResponse<T> {

    @Schema(description = "İşlem başarı durumu", example = "true")
    private boolean success;

    @Schema(description = "Yanıt verisi")
    private T data;

    @Schema(description = "İşlem sonuç mesajı", example = "İşlem başarılı")
    private String message;

    @Schema(description = "İstek takip kimliği", example = "550e8400-e29b-41d4-a716-446655440000")
    private String requestId;

    @Schema(description = "Veri güncellik zaman damgası", example = "2026-06-24T14:30:00")
    private LocalDateTime dataDate;
}


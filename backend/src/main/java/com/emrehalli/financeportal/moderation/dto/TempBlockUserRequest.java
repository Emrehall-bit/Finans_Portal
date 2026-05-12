package com.emrehalli.financeportal.moderation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TempBlockUserRequest {

    private String reason;

    @NotNull(message = "blockedUntil is required")
    private LocalDateTime blockedUntil;
}

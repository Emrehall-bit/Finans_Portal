package com.emrehalli.financeportal.watchlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Takip listesine enstrüman ekleme isteği")
public class AddWatchlistRequest {

    @NotBlank(message = "{validation.instrumentCode.blank}")
    @Schema(description = "Enstrüman kodu", example = "THYAO", requiredMode = Schema.RequiredMode.REQUIRED)
    private String instrumentCode;
}


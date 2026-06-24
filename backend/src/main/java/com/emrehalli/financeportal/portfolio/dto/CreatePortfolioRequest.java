package com.emrehalli.financeportal.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Yeni portföy oluşturma isteği")
public class CreatePortfolioRequest {

    @NotBlank(message = "{validation.portfolioName.blank}")
    @Size(max = 100, message = "{validation.portfolioName.max}")
    @Schema(description = "Portföy adı", example = "Uzun Vadeli Yatırımlar", requiredMode = Schema.RequiredMode.REQUIRED)
    private String portfolioName;
}


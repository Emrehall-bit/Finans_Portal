package com.emrehalli.financeportal.portfolio.dto;

import com.emrehalli.financeportal.portfolio.entity.PortfolioVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePortfolioRequest {

    @NotBlank(message = "Portfolio name cannot be blank")
    @Size(max = 100, message = "Portfolio name cannot exceed 100 characters")
    private String portfolioName;

    @NotNull(message = "Visibility status is required")
    private PortfolioVisibility visibilityStatus;
}



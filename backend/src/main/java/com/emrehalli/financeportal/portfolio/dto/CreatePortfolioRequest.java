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

    @NotBlank(message = "{validation.portfolioName.blank}")
    @Size(max = 100, message = "{validation.portfolioName.max}")
    private String portfolioName;

    @NotNull(message = "{validation.visibility.required}")
    private PortfolioVisibility visibilityStatus;
}



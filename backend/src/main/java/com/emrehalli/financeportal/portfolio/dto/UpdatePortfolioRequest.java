package com.emrehalli.financeportal.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePortfolioRequest {

    @NotBlank(message = "{validation.portfolioName.blank}")
    @Size(max = 100, message = "{validation.portfolioName.max}")
    private String portfolioName;
}




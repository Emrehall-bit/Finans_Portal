package com.emrehalli.financeportal.portfolio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CreatePortfolioHoldingRequest {

    @NotBlank(message = "{validation.instrumentCode.blank}")
    private String instrumentCode;

    @NotNull(message = "{validation.quantity.required}")
    @DecimalMin(value = "0.0001", message = "{validation.quantity.positive}")
    private BigDecimal quantity;

    @NotNull(message = "{validation.buyPrice.required}")
    @DecimalMin(value = "0.0001", message = "{validation.buyPrice.positive}")
    private BigDecimal buyPrice;

    private LocalDate purchaseDate;

    public String getInstrumentCode() {
        return instrumentCode;
    }

    public void setInstrumentCode(String instrumentCode) {
        this.instrumentCode = instrumentCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getBuyPrice() {
        return buyPrice;
    }

    public void setBuyPrice(BigDecimal buyPrice) {
        this.buyPrice = buyPrice;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }
}







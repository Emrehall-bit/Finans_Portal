package com.emrehalli.financeportal.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Portföye yeni varlık ekleme isteği")
public class CreatePortfolioHoldingRequest {

    @NotBlank(message = "{validation.instrumentCode.blank}")
    @Schema(description = "Enstrüman kodu", example = "THYAO", requiredMode = Schema.RequiredMode.REQUIRED)
    private String instrumentCode;

    @NotNull(message = "{validation.quantity.required}")
    @DecimalMin(value = "0.0001", message = "{validation.quantity.positive}")
    @Schema(description = "Miktar", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    @NotNull(message = "{validation.buyPrice.required}")
    @DecimalMin(value = "0.0001", message = "{validation.buyPrice.positive}")
    @Schema(description = "Alış fiyatı (TRY)", example = "185.50", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal buyPrice;

    @Schema(description = "Alış tarihi", example = "2026-01-15")
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


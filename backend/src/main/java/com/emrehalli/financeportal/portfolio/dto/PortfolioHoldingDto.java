package com.emrehalli.financeportal.portfolio.dto;

import java.math.BigDecimal;

public class PortfolioHoldingDto {

    private String instrumentCode;
    private BigDecimal quantity;
    private BigDecimal averageBuyPrice;
    private BigDecimal currentPrice;
    private BigDecimal currentValue;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercent;

    public PortfolioHoldingDto() {
    }

    public PortfolioHoldingDto(String instrumentCode, BigDecimal quantity, BigDecimal averageBuyPrice,
                               BigDecimal currentPrice, BigDecimal currentValue, BigDecimal profitLoss,
                               BigDecimal profitLossPercent) {
        this.instrumentCode = instrumentCode;
        this.quantity = quantity;
        this.averageBuyPrice = averageBuyPrice;
        this.currentPrice = currentPrice;
        this.currentValue = currentValue;
        this.profitLoss = profitLoss;
        this.profitLossPercent = profitLossPercent;
    }

    public String getInstrumentCode() { return instrumentCode; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAverageBuyPrice() { return averageBuyPrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public BigDecimal getProfitLoss() { return profitLoss; }
    public BigDecimal getProfitLossPercent() { return profitLossPercent; }

    public void setInstrumentCode(String instrumentCode) { this.instrumentCode = instrumentCode; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public void setAverageBuyPrice(BigDecimal averageBuyPrice) { this.averageBuyPrice = averageBuyPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }
    public void setProfitLoss(BigDecimal profitLoss) { this.profitLoss = profitLoss; }
    public void setProfitLossPercent(BigDecimal profitLossPercent) { this.profitLossPercent = profitLossPercent; }
}
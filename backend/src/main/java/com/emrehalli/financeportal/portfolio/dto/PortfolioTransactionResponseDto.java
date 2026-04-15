package com.emrehalli.financeportal.portfolio.dto;

import com.emrehalli.financeportal.portfolio.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PortfolioTransactionResponseDto {

    private Long id;
    private Long portfolioId;
    private String instrumentCode;
    private TransactionType transactionType;
    private BigDecimal quantity;
    private BigDecimal price;
    private LocalDateTime transactionTime;
    private LocalDateTime createdAt;

    public PortfolioTransactionResponseDto() {
    }

    public PortfolioTransactionResponseDto(Long id, Long portfolioId, String instrumentCode,
                                           TransactionType transactionType, BigDecimal quantity,
                                           BigDecimal price, LocalDateTime transactionTime,
                                           LocalDateTime createdAt) {
        this.id = id;
        this.portfolioId = portfolioId;
        this.instrumentCode = instrumentCode;
        this.transactionType = transactionType;
        this.quantity = quantity;
        this.price = price;
        this.transactionTime = transactionTime;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getPortfolioId() { return portfolioId; }
    public String getInstrumentCode() { return instrumentCode; }
    public TransactionType getTransactionType() { return transactionType; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public LocalDateTime getTransactionTime() { return transactionTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
    public void setInstrumentCode(String instrumentCode) { this.instrumentCode = instrumentCode; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setTransactionTime(LocalDateTime transactionTime) { this.transactionTime = transactionTime; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
package com.emrehalli.financeportal.portfolio.mapper;

import com.emrehalli.financeportal.portfolio.dto.PortfolioTransactionResponseDto;
import com.emrehalli.financeportal.portfolio.entity.PortfolioTransaction;
import org.springframework.stereotype.Component;

@Component
public class PortfolioTransactionMapper {

    public PortfolioTransactionResponseDto toDto(PortfolioTransaction transaction) {
        if (transaction == null) {
            return null;
        }

        return new PortfolioTransactionResponseDto(
                transaction.getId(),
                transaction.getPortfolio() != null ? transaction.getPortfolio().getId() : null,
                transaction.getInstrumentCode(),
                transaction.getTransactionType(),
                transaction.getQuantity(),
                transaction.getPrice(),
                transaction.getTransactionTime(),
                transaction.getCreatedAt()
        );
    }
}
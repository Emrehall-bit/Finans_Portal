package com.emrehalli.financeportal.portfolio.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PortfolioVisibilityConverter implements AttributeConverter<PortfolioVisibility, String> {

    @Override
    public String convertToDatabaseColumn(PortfolioVisibility visibility) {
        return visibility == null ? PortfolioVisibility.PRIVATE.name() : visibility.name();
    }

    @Override
    public PortfolioVisibility convertToEntityAttribute(String dbData) {
        return PortfolioVisibility.fromDbValue(dbData);
    }
}




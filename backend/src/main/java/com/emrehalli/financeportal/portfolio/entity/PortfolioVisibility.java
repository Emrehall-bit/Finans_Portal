package com.emrehalli.financeportal.portfolio.entity;

public enum PortfolioVisibility {
    PRIVATE,
    PUBLIC;

    public static PortfolioVisibility fromDbValue(String value) {
        if (value == null || value.isBlank()) {
            return PRIVATE;
        }

        try {
            return PortfolioVisibility.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PRIVATE;
        }
    }
}




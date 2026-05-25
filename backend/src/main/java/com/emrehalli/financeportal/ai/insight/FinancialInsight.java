package com.emrehalli.financeportal.ai.insight;

public record FinancialInsight(String text, Category category) {

    public enum Category { STRENGTH, WEAKNESS, RISK, NEUTRAL, GROWTH, VALUATION }

    public static FinancialInsight strength(String text)  { return new FinancialInsight(text, Category.STRENGTH); }
    public static FinancialInsight weakness(String text)  { return new FinancialInsight(text, Category.WEAKNESS); }
    public static FinancialInsight risk(String text)      { return new FinancialInsight(text, Category.RISK); }
    public static FinancialInsight neutral(String text)   { return new FinancialInsight(text, Category.NEUTRAL); }
    public static FinancialInsight growth(String text)    { return new FinancialInsight(text, Category.GROWTH); }
    public static FinancialInsight valuation(String text) { return new FinancialInsight(text, Category.VALUATION); }
}




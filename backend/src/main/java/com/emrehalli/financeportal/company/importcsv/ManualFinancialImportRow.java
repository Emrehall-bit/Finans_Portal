package com.emrehalli.financeportal.company.importcsv;

public record ManualFinancialImportRow(
        int lineNumber,
        String tickerCode,
        String companyReference,
        String periodYear,
        String reportType,
        String publishedAt,
        String sourceUrl,
        String itemKey,
        String rawLabel,
        String value,
        String currency,
        String unitMultiplier,
        String sharesOutstanding
) {
}

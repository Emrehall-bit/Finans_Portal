package com.emrehalli.financeportal.company.provider.kap;

import com.emrehalli.financeportal.company.dto.SkippedReportReasonDto;
import com.emrehalli.financeportal.company.provider.kap.dto.KapFinancialReportDto;

import java.util.List;

public record KapFinancialReportProviderResult(
        List<KapFinancialReportDto> reports,
        List<SkippedReportReasonDto> skippedReasons
) {}

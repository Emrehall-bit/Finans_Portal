package com.emrehalli.financeportal.company.kap.client;

import com.emrehalli.financeportal.company.kap.dto.SkippedReportReasonDto;
import com.emrehalli.financeportal.company.kap.dto.KapFinancialReportDto;

import java.util.List;

public record KapFinancialReportProviderResult(
        List<KapFinancialReportDto> reports,
        List<SkippedReportReasonDto> skippedReasons
) {}




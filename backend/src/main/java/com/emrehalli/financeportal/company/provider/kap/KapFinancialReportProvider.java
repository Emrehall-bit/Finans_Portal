package com.emrehalli.financeportal.company.provider.kap;

import com.emrehalli.financeportal.company.dto.SkippedReportReasonDto;
import com.emrehalli.financeportal.company.entity.CompanyDisclosure;
import com.emrehalli.financeportal.company.entity.CompanyProfile;
import com.emrehalli.financeportal.company.enums.DisclosureType;
import com.emrehalli.financeportal.company.enums.ReportType;
import com.emrehalli.financeportal.company.provider.kap.dto.KapFinancialReportDto;
import com.emrehalli.financeportal.company.repository.CompanyDisclosureRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KapFinancialReportProvider {

    private static final Logger logger = LogManager.getLogger(KapFinancialReportProvider.class);

    private static final Pattern YEAR_SLASH_MONTH = Pattern.compile("\\b(20\\d{2})/(\\d{1,2})\\b");
    private static final Pattern END_DATE = Pattern.compile("\\b(31\\.12|30\\.09|30\\.06|31\\.03)\\.(20\\d{2})\\b");
    private static final Pattern YEAR_ONLY = Pattern.compile("\\b(20\\d{2})\\b");

    private final CompanyDisclosureRepository disclosureRepository;

    public KapFinancialReportProvider(CompanyDisclosureRepository disclosureRepository) {
        this.disclosureRepository = disclosureRepository;
    }

    public KapFinancialReportProviderResult findCandidateReports(CompanyProfile company) {
        List<CompanyDisclosure> financialDisclosures = disclosureRepository
                .findByCompanyTickerCodeIgnoreCaseAndDisclosureType(
                        company.getTickerCode(), DisclosureType.FINANCIAL);

        List<KapFinancialReportDto> reports = new ArrayList<>();
        List<SkippedReportReasonDto> skipped = new ArrayList<>();

        for (CompanyDisclosure d : financialDisclosures) {
            Optional<KapFinancialReportDto> result = detectPeriod(company, d);
            if (result.isPresent()) {
                reports.add(result.get());
            } else {
                skipped.add(SkippedReportReasonDto.builder()
                        .title(d.getTitle())
                        .kapUrl(d.getKapUrl())
                        .reason("Dönem tespit edilemedi (kapYear=" + d.getKapYear()
                                + ", kapDonem=" + d.getKapDonem()
                                + ", title=" + d.getTitle() + ")")
                        .build());
                logger.debug("Period not detected. ticker={}, kapYear={}, kapDonem={}, title={}",
                        company.getTickerCode(), d.getKapYear(), d.getKapDonem(), d.getTitle());
            }
        }

        logger.info("Candidate report detection done. ticker={}, financial={}, mapped={}, skipped={}",
                company.getTickerCode(), financialDisclosures.size(), reports.size(), skipped.size());

        return new KapFinancialReportProviderResult(reports, skipped);
    }

    private Optional<KapFinancialReportDto> detectPeriod(CompanyProfile company, CompanyDisclosure disclosure) {
        // 1. Prefer structured kapYear/kapDonem from KAP JSON metadata
        if (disclosure.getKapYear() != null && disclosure.getKapDonem() != null) {
            return buildByDonem(company, disclosure, disclosure.getKapYear(), disclosure.getKapDonem());
        }

        // 2. Fallback: regex on title
        return detectPeriodFromTitle(company, disclosure);
    }

    private Optional<KapFinancialReportDto> buildByDonem(CompanyProfile company,
                                                          CompanyDisclosure disclosure,
                                                          int year, int donem) {
        int quarter;
        ReportType reportType;
        switch (donem) {
            case 1 -> { quarter = 1; reportType = ReportType.Q1; }
            case 2 -> { quarter = 2; reportType = ReportType.Q2; }
            case 3 -> { quarter = 3; reportType = ReportType.Q3; }
            case 4 -> { quarter = 4; reportType = ReportType.ANNUAL; }
            default -> {
                logger.debug("Unsupported kapDonem={}. ticker={}, title={}",
                        donem, company.getTickerCode(), disclosure.getTitle());
                return Optional.empty();
            }
        }
        return Optional.of(buildDto(company, disclosure, year, quarter, reportType));
    }

    private Optional<KapFinancialReportDto> detectPeriodFromTitle(CompanyProfile company,
                                                                    CompanyDisclosure disclosure) {
        String title = disclosure.getTitle();
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }

        String lower = title.replace('İ', 'i').toLowerCase(Locale.ROOT);

        Matcher slashMatcher = YEAR_SLASH_MONTH.matcher(title);
        if (slashMatcher.find()) {
            int year = Integer.parseInt(slashMatcher.group(1));
            int month = Integer.parseInt(slashMatcher.group(2));
            return buildByMonth(company, disclosure, year, month);
        }

        Matcher endDateMatcher = END_DATE.matcher(title);
        if (endDateMatcher.find()) {
            String dayMonth = endDateMatcher.group(1);
            int year = Integer.parseInt(endDateMatcher.group(2));
            return switch (dayMonth) {
                case "31.12" -> buildByMonth(company, disclosure, year, 12);
                case "30.09" -> buildByMonth(company, disclosure, year, 9);
                case "30.06" -> buildByMonth(company, disclosure, year, 6);
                case "31.03" -> buildByMonth(company, disclosure, year, 3);
                default -> Optional.empty();
            };
        }

        Matcher yearMatcher = YEAR_ONLY.matcher(title);
        if (!yearMatcher.find()) {
            return Optional.empty();
        }
        int year = Integer.parseInt(yearMatcher.group(1));

        if (containsAny(lower, "yillik", "annual", "12 aylik", "12-aylik", "yil sonu")) {
            return buildByMonth(company, disclosure, year, 12);
        }
        if (containsAny(lower, "9 aylik", "dokuz aylik", "ucuncu ceyrek", "q3", "third quarter")) {
            return buildByMonth(company, disclosure, year, 9);
        }
        if (containsAny(lower, "6 aylik", "alti aylik", "ikinci ceyrek", "q2", "second quarter")) {
            return buildByMonth(company, disclosure, year, 6);
        }
        if (containsAny(lower, "3 aylik", "uc aylik", "birinci ceyrek", "q1", "first quarter")) {
            return buildByMonth(company, disclosure, year, 3);
        }

        return Optional.empty();
    }

    private Optional<KapFinancialReportDto> buildByMonth(CompanyProfile company,
                                                          CompanyDisclosure disclosure,
                                                          int year, int month) {
        int quarter;
        ReportType reportType;
        switch (month) {
            case 12 -> { quarter = 4; reportType = ReportType.ANNUAL; }
            case 9  -> { quarter = 3; reportType = ReportType.Q3; }
            case 6  -> { quarter = 2; reportType = ReportType.Q2; }
            case 3  -> { quarter = 1; reportType = ReportType.Q1; }
            default -> {
                logger.debug("Unsupported month={}. ticker={}", month, company.getTickerCode());
                return Optional.empty();
            }
        }
        return Optional.of(buildDto(company, disclosure, year, quarter, reportType));
    }

    private KapFinancialReportDto buildDto(CompanyProfile company, CompanyDisclosure disclosure,
                                            int year, int quarter, ReportType reportType) {
        return KapFinancialReportDto.builder()
                .companyId(company.getId())
                .periodYear(year)
                .periodQuarter(quarter)
                .reportType(reportType)
                .sourceUrl(disclosure.getKapUrl())
                .publishedAt(disclosure.getPublishedAt() != null
                        ? disclosure.getPublishedAt().toLocalDate()
                        : null)
                .build();
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
    }
}

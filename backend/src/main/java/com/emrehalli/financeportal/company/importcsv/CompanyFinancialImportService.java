package com.emrehalli.financeportal.company.importcsv;

import com.emrehalli.financeportal.company.domain.entity.CompanyFinancialReport;
import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.domain.enums.FinancialItemKey;
import com.emrehalli.financeportal.company.domain.enums.ParseStatus;
import com.emrehalli.financeportal.company.domain.enums.ReportType;
import com.emrehalli.financeportal.company.dto.importcsv.ManualFinancialImportError;
import com.emrehalli.financeportal.company.dto.importcsv.ManualFinancialImportResponse;
import com.emrehalli.financeportal.company.persistence.CompanyFinancialReportRepository;
import com.emrehalli.financeportal.company.persistence.CompanyFinancialValueRepository;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import com.emrehalli.financeportal.company.persistence.CompanyRatioRepository;
import com.emrehalli.financeportal.company.service.CompanyRatioService;
import com.emrehalli.financeportal.company.support.FinancialReportUpsertSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class CompanyFinancialImportService {

    private final CompanyProfileRepository profileRepository;
    private final CompanyFinancialReportRepository reportRepository;
    private final CompanyFinancialValueRepository valueRepository;
    private final CompanyRatioRepository ratioRepository;
    private final CompanyRatioService ratioService;
    private final ManualFinancialCsvReader csvReader;
    private final FinancialReportUpsertSupport upsertSupport;

    public CompanyFinancialImportService(CompanyProfileRepository profileRepository,
                                         CompanyFinancialReportRepository reportRepository,
                                         CompanyFinancialValueRepository valueRepository,
                                         CompanyRatioRepository ratioRepository,
                                         CompanyRatioService ratioService,
                                         ManualFinancialCsvReader csvReader,
                                         FinancialReportUpsertSupport upsertSupport) {
        this.profileRepository = profileRepository;
        this.reportRepository = reportRepository;
        this.valueRepository = valueRepository;
        this.ratioRepository = ratioRepository;
        this.ratioService = ratioService;
        this.csvReader = csvReader;
        this.upsertSupport = upsertSupport;
    }

    @Transactional
    public ManualFinancialImportResponse importCsv(MultipartFile file,
                                                   boolean dryRun,
                                                   boolean replaceExisting,
                                                   boolean recalculateRatios,
                                                   boolean overwriteShareCount) {
        ManualFinancialCsvReader.CsvReadResult readResult = csvReader.read(file);
        return importRows(readResult.rows(), readResult.errors(), dryRun, replaceExisting, recalculateRatios, overwriteShareCount);
    }

    private ManualFinancialImportResponse importRows(List<ManualFinancialImportRow> rows,
                                                     List<ManualFinancialImportError> readErrors,
                                                     boolean dryRun,
                                                     boolean replaceExisting,
                                                     boolean recalculateRatios,
                                                     boolean overwriteShareCount) {
        List<ManualFinancialImportError> validationErrors = new ArrayList<>(readErrors);
        if (!validationErrors.isEmpty()) {
            return emptyResponse(dryRun, validationErrors);
        }

        ImportPreparation preparation = prepare(rows, validationErrors, overwriteShareCount);
        ImportCounters counters = new ImportCounters(
                preparation.createdReports(),
                preparation.updatedReports(),
                preparation.createdValues(),
                preparation.updatedValues(),
                replaceExisting ? preparation.deletedStaleValues() : 0
        );

        List<String> recalculatedTickers = new ArrayList<>();
        if (!dryRun) {
            applyImport(preparation.reportImports(), replaceExisting);
            applyShareCountUpdates(preparation.shareCountUpdates());
            if (recalculateRatios) {
                recalculatedTickers = recalculate(preparation.affectedTickers(), validationErrors);
            }
        }

        return ManualFinancialImportResponse.builder()
                .dryRun(dryRun)
                .createdReports(counters.createdReports())
                .updatedReports(counters.updatedReports())
                .createdValues(counters.createdValues())
                .updatedValues(counters.updatedValues())
                .deletedStaleValues(counters.deletedStaleValues())
                .recalculatedTickers(recalculatedTickers)
                .preservedRealRatios(preparation.preservedRealRatios())
                .createdMockRatios(recalculatedTickers)
                .skippedExisting(preparation.skippedExisting())
                .updatedShareCounts(preparation.updatedShareCounts())
                .skippedShareCounts(preparation.skippedShareCounts())
                .missingShareCountWarnings(preparation.missingShareCountWarnings())
                .validationErrors(validationErrors)
                .build();
    }

    private ManualFinancialImportResponse emptyResponse(boolean dryRun, List<ManualFinancialImportError> validationErrors) {
        return ManualFinancialImportResponse.builder()
                .dryRun(dryRun)
                .createdReports(0)
                .updatedReports(0)
                .createdValues(0)
                .updatedValues(0)
                .deletedStaleValues(0)
                .recalculatedTickers(List.of())
                .preservedRealRatios(List.of())
                .createdMockRatios(List.of())
                .skippedExisting(List.of())
                .updatedShareCounts(List.of())
                .skippedShareCounts(List.of())
                .missingShareCountWarnings(List.of())
                .validationErrors(validationErrors)
                .build();
    }

    private ImportPreparation prepare(List<ManualFinancialImportRow> rows,
                                      List<ManualFinancialImportError> validationErrors,
                                      boolean overwriteShareCount) {
        Map<String, CompanyProfile> companyCache = new LinkedHashMap<>();
        Map<ReportKey, PreparedReportImport> grouped = new LinkedHashMap<>();
        Map<String, ShareCountDecision> shareCountDecisions = new LinkedHashMap<>();

        for (ManualFinancialImportRow row : rows) {
            ValidatedRow validatedRow = validateRow(row, companyCache, validationErrors);
            if (validatedRow == null) {
                continue;
            }

            registerShareCount(validatedRow, shareCountDecisions, validationErrors);

            ReportKey reportKey = new ReportKey(
                    validatedRow.company().getId(),
                    validatedRow.periodYear(),
                    validatedRow.periodQuarter(),
                    validatedRow.reportType());

            PreparedReportImport reportImport = grouped.computeIfAbsent(reportKey, key -> new PreparedReportImport(
                    validatedRow.company(),
                    validatedRow.periodYear(),
                    validatedRow.periodQuarter(),
                    validatedRow.reportType(),
                    validatedRow.publishedAt(),
                    validatedRow.sourceUrl(),
                    new LinkedHashMap<>()
            ));

            reportImport.setPublishedAt(validatedRow.publishedAt());
            reportImport.setSourceUrl(validatedRow.sourceUrl());
            reportImport.items().put(validatedRow.itemKey(), new PreparedValueImport(
                    validatedRow.itemKey(),
                    validatedRow.rawLabel(),
                    validatedRow.value(),
                    validatedRow.currency(),
                    validatedRow.unitMultiplier()
            ));
        }

        List<PreparedReportImport> allReportImports = grouped.values().stream()
                .sorted(Comparator.comparing((PreparedReportImport report) -> report.company().getTickerCode())
                        .thenComparing(PreparedReportImport::periodYear)
                        .thenComparing(PreparedReportImport::periodQuarter))
                .toList();

        List<String> preservedRealRatios = resolvePreservedRealRatios(allReportImports);
        Set<String> preservedTickerSet = new LinkedHashSet<>(preservedRealRatios);
        List<String> skippedExisting = new ArrayList<>(preservedRealRatios);
        List<PreparedReportImport> reportImports = allReportImports.stream()
                .filter(reportImport -> !preservedTickerSet.contains(reportImport.company().getTickerCode()))
                .toList();

        int createdReports = 0;
        int updatedReports = 0;
        int createdValues = 0;
        int updatedValues = 0;
        int deletedStaleValues = 0;
        Set<String> affectedTickers = new LinkedHashSet<>();
        List<ShareCountUpdate> shareCountUpdates = new ArrayList<>();
        List<String> updatedShareCounts = new ArrayList<>();
        List<String> skippedShareCounts = new ArrayList<>();
        List<String> missingShareCountWarnings = new ArrayList<>();

        for (PreparedReportImport reportImport : reportImports) {
            affectedTickers.add(reportImport.company().getTickerCode());

            Optional<CompanyFinancialReport> existingReport = reportRepository
                    .findByCompanyIdAndPeriodYearAndPeriodQuarterAndReportType(
                            reportImport.company().getId(),
                            reportImport.periodYear(),
                            reportImport.periodQuarter(),
                            reportImport.reportType());
            reportImport.setExistingReport(existingReport.orElse(null));
            if (existingReport.isPresent()) {
                updatedReports++;
            } else {
                createdReports++;
            }

            Map<String, String> existingValues = existingReport
                    .map(report -> valueRepository.findByReportId(report.getId()))
                    .orElse(List.of())
                    .stream()
                    .collect(LinkedHashMap::new, (map, value) -> map.put(value.getItemKey(), value.getItemKey()), Map::putAll);

            for (PreparedValueImport item : reportImport.items().values()) {
                if (existingValues.containsKey(item.itemKey().name())) {
                    updatedValues++;
                } else {
                    createdValues++;
                }
            }

            Set<String> staleKeys = new LinkedHashSet<>(existingValues.keySet());
            staleKeys.removeAll(reportImport.items().keySet().stream()
                    .map(Enum::name)
                    .collect(LinkedHashSet::new, Set::add, Set::addAll));
            reportImport.setStaleItemKeys(staleKeys);
            deletedStaleValues += staleKeys.size();
        }

        for (ShareCountDecision decision : shareCountDecisions.values()) {
            if (preservedTickerSet.contains(decision.company().getTickerCode())) {
                continue;
            }
            if (decision.firstValidValue() != null) {
                BigDecimal existing = decision.company().getSharesOutstanding();
                if (existing == null) {
                    shareCountUpdates.add(new ShareCountUpdate(decision.company(), decision.firstValidValue()));
                    updatedShareCounts.add(decision.company().getTickerCode());
                } else if (existing.compareTo(decision.firstValidValue()) != 0 && overwriteShareCount) {
                    shareCountUpdates.add(new ShareCountUpdate(decision.company(), decision.firstValidValue()));
                    updatedShareCounts.add(decision.company().getTickerCode());
                } else {
                    skippedShareCounts.add(decision.company().getTickerCode());
                }
            } else if (decision.sawShareColumn()) {
                skippedShareCounts.add(decision.company().getTickerCode());
            }
        }

        for (String ticker : affectedTickers) {
            ShareCountDecision decision = shareCountDecisions.get(ticker);
            boolean updatedNow = updatedShareCounts.contains(ticker);
            boolean hasExisting = decision != null
                    && decision.company().getSharesOutstanding() != null
                    && decision.company().getSharesOutstanding().compareTo(BigDecimal.ZERO) > 0;
            boolean hasIncoming = decision != null
                    && decision.firstValidValue() != null
                    && decision.firstValidValue().compareTo(BigDecimal.ZERO) > 0;
            if (!updatedNow && !hasExisting && !hasIncoming) {
                missingShareCountWarnings.add(ticker + ": share count/paid capital missing");
            }
        }

        return new ImportPreparation(
                reportImports,
                createdReports,
                updatedReports,
                createdValues,
                updatedValues,
                deletedStaleValues,
                new ArrayList<>(affectedTickers),
                preservedRealRatios,
                skippedExisting,
                shareCountUpdates,
                updatedShareCounts,
                skippedShareCounts,
                missingShareCountWarnings
        );
    }

    private ValidatedRow validateRow(ManualFinancialImportRow row,
                                     Map<String, CompanyProfile> companyCache,
                                     List<ManualFinancialImportError> validationErrors) {
        String tickerCode = normalizeKey(row.tickerCode());
        String periodYearRaw = row.periodYear();
        String reportTypeRaw = normalizeKey(row.reportType());
        String publishedAtRaw = row.publishedAt();
        String sourceUrlRaw = row.sourceUrl();
        String itemKeyRaw = normalizeKey(row.itemKey());
        String rawLabel = blankToNull(row.rawLabel());
        String valueRaw = row.value();
        String currencyRaw = normalizeKey(row.currency());
        String unitMultiplierRaw = row.unitMultiplier();
        String sharesOutstandingRaw = row.sharesOutstanding();

        CompanyProfile company = null;
        Integer periodYear = null;
        ReportType reportType = null;
        Integer periodQuarter = null;
        LocalDate publishedAt = null;
        FinancialItemKey itemKey = null;
        BigDecimal value = null;
        Integer unitMultiplier = 1;
        BigDecimal sharesOutstanding = null;

        if (tickerCode == null) {
            validationErrors.add(error(row.lineNumber(), null, null, reportTypeRaw, itemKeyRaw, "ticker_code", null, "ticker_code zorunlu."));
        } else {
            company = companyCache.computeIfAbsent(tickerCode, key -> profileRepository.findByTickerCodeIgnoreCase(key).orElse(null));
            if (company == null) {
                validationErrors.add(error(row.lineNumber(), tickerCode, null, reportTypeRaw, itemKeyRaw, "ticker_code", tickerCode, "Sirket company_profiles icinde bulunamadi."));
            }
        }

        try {
            periodYear = Integer.valueOf(periodYearRaw);
        } catch (Exception e) {
            validationErrors.add(error(row.lineNumber(), tickerCode, null, reportTypeRaw, itemKeyRaw, "period_year", periodYearRaw, "period_year parse edilemedi."));
        }

        if (reportTypeRaw == null) {
            validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, null, itemKeyRaw, "report_type", row.reportType(), "report_type zorunlu."));
        } else {
            try {
                reportType = ReportType.valueOf(reportTypeRaw);
                if (reportType == ReportType.Q4) {
                    validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "report_type", reportTypeRaw, "Yalnizca Q1, Q2, Q3, ANNUAL kabul edilir."));
                } else {
                    periodQuarter = mapPeriodQuarter(reportType);
                }
            } catch (IllegalArgumentException e) {
                validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "report_type", reportTypeRaw, "Gecersiz report_type."));
            }
        }

        if (publishedAtRaw != null && !publishedAtRaw.isBlank()) {
            try {
                publishedAt = LocalDate.parse(publishedAtRaw.trim());
            } catch (DateTimeParseException e) {
                validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "published_at", publishedAtRaw, "published_at YYYY-MM-DD formatinda olmali."));
            }
        }

        if (itemKeyRaw == null) {
            validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, null, "item_key", row.itemKey(), "item_key zorunlu."));
        } else {
            try {
                itemKey = FinancialItemKey.valueOf(itemKeyRaw);
            } catch (IllegalArgumentException e) {
                validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "item_key", itemKeyRaw, "FinancialItemKey enum icinde bulunamadi."));
            }
        }

        if (valueRaw == null || valueRaw.isBlank()) {
            validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "value", valueRaw, "value zorunlu."));
        } else {
            try {
                value = new BigDecimal(valueRaw.trim());
            } catch (NumberFormatException e) {
                validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "value", valueRaw, "value parse edilemedi."));
            }
        }

        if (unitMultiplierRaw != null && !unitMultiplierRaw.isBlank()) {
            try {
                unitMultiplier = Integer.valueOf(unitMultiplierRaw.trim());
            } catch (NumberFormatException e) {
                validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "unit_multiplier", unitMultiplierRaw, "unit_multiplier parse edilemedi."));
            }
        }

        if (sharesOutstandingRaw != null && !sharesOutstandingRaw.isBlank()) {
            try {
                sharesOutstanding = new BigDecimal(sharesOutstandingRaw.trim());
                if (sharesOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
                    validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "shares_outstanding", sharesOutstandingRaw, "shares_outstanding pozitif olmali."));
                    sharesOutstanding = null;
                }
            } catch (NumberFormatException e) {
                validationErrors.add(error(row.lineNumber(), tickerCode, periodYear, reportTypeRaw, itemKeyRaw, "shares_outstanding", sharesOutstandingRaw, "shares_outstanding parse edilemedi."));
            }
        }

        if (company == null || periodYear == null || reportType == null || periodQuarter == null || itemKey == null || value == null) {
            return null;
        }

        String sourceUrl = blankToNull(sourceUrlRaw);
        if (sourceUrl == null) {
            sourceUrl = "manual://csv";
        }

        String currency = blankToNull(currencyRaw);
        if (currency == null) {
            currency = "TRY";
        }

        return new ValidatedRow(
                company,
                row.lineNumber(),
                periodYear,
                periodQuarter,
                reportType,
                publishedAt,
                sourceUrl,
                itemKey,
                rawLabel,
                value,
                currency.toUpperCase(Locale.ROOT),
                unitMultiplier,
                sharesOutstanding,
                blankToNull(sharesOutstandingRaw) != null
        );
    }

    private void applyImport(List<PreparedReportImport> reportImports, boolean replaceExisting) {
        OffsetDateTime now = OffsetDateTime.now();
        for (PreparedReportImport reportImport : reportImports) {
            CompanyFinancialReport report = upsertSupport.upsertReport(
                    reportImport.company(),
                    reportImport.periodYear(),
                    reportImport.periodQuarter(),
                    reportImport.reportType(),
                    reportImport.sourceUrl(),
                    reportImport.publishedAt(),
                    ParseStatus.SUCCESS,
                    now
            ).report();

            for (PreparedValueImport item : reportImport.items().values()) {
                upsertSupport.upsertValue(
                        report,
                        item.itemKey().name(),
                        item.rawLabel(),
                        item.value(),
                        item.currency(),
                        item.unitMultiplier(),
                        true
                );
            }

            if (replaceExisting && reportImport.existingReport() != null && !reportImport.staleItemKeys().isEmpty()) {
                upsertSupport.deleteMissingValues(report.getId(), reportImport.items().keySet().stream()
                        .map(Enum::name)
                        .collect(LinkedHashSet::new, Set::add, Set::addAll));
            }
        }
    }

    private List<String> recalculate(List<String> tickers, List<ManualFinancialImportError> validationErrors) {
        List<String> recalculatedTickers = new ArrayList<>();
        for (String ticker : tickers) {
            try {
                var result = ratioService.calculateForTicker(ticker);
                if (result.isCalculated()) {
                    recalculatedTickers.add(ticker);
                    if (result.getMissingReason() != null && !result.getMissingReason().isBlank()) {
                        validationErrors.add(ManualFinancialImportError.builder()
                                .tickerCode(ticker)
                                .fieldName("recalculateRatios")
                                .message(result.getMissingReason())
                                .build());
                    }
                } else {
                    validationErrors.add(ManualFinancialImportError.builder()
                            .tickerCode(ticker)
                            .fieldName("recalculateRatios")
                            .message(result.getFailedReason())
                            .build());
                }
            } catch (Exception e) {
                validationErrors.add(ManualFinancialImportError.builder()
                        .tickerCode(ticker)
                        .fieldName("recalculateRatios")
                        .message("Oran hesaplama hatasi: " + e.getMessage())
                        .build());
            }
        }
        return recalculatedTickers;
    }

    private List<String> resolvePreservedRealRatios(List<PreparedReportImport> reportImports) {
        List<String> preserved = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (PreparedReportImport reportImport : reportImports) {
            String ticker = reportImport.company().getTickerCode();
            if (!seen.add(ticker)) {
                continue;
            }
            if (ratioRepository.findTopByCompanyTickerCodeIgnoreCaseOrderByCalculatedAtDesc(ticker).isPresent()) {
                preserved.add(ticker);
            }
        }
        return preserved;
    }

    private void applyShareCountUpdates(List<ShareCountUpdate> shareCountUpdates) {
        for (ShareCountUpdate update : shareCountUpdates) {
            update.company().setSharesOutstanding(update.sharesOutstanding());
            profileRepository.save(update.company());
        }
    }

    private void registerShareCount(ValidatedRow row,
                                    Map<String, ShareCountDecision> shareCountDecisions,
                                    List<ManualFinancialImportError> validationErrors) {
        ShareCountDecision decision = shareCountDecisions.computeIfAbsent(
                row.company().getTickerCode(),
                key -> new ShareCountDecision(row.company())
        );

        if (row.sharesOutstandingProvided()) {
            decision.setSawShareColumn(true);
        }
        if (row.sharesOutstanding() == null) {
            return;
        }
        if (decision.firstValidValue() == null) {
            decision.setFirstValidValue(row.sharesOutstanding());
            return;
        }
        if (decision.firstValidValue().compareTo(row.sharesOutstanding()) != 0) {
            validationErrors.add(ManualFinancialImportError.builder()
                    .lineNumber(row.sourceLineNumber())
                    .tickerCode(row.company().getTickerCode())
                    .periodYear(row.periodYear())
                    .reportType(row.reportType().name())
                    .itemKey(row.itemKey().name())
                    .fieldName("shares_outstanding")
                    .rawValue(row.sharesOutstanding().toPlainString())
                    .message("shares_outstanding icin farkli bir deger bulundu. Ilk gecerli deger kullanilacak.")
                    .build());
        }
    }

    private Integer mapPeriodQuarter(ReportType reportType) {
        return switch (reportType) {
            case Q1 -> 1;
            case Q2 -> 2;
            case Q3 -> 3;
            case ANNUAL -> 4;
            case Q4 -> null;
        };
    }

    private ManualFinancialImportError error(Integer lineNumber,
                                             String tickerCode,
                                             Integer periodYear,
                                             String reportType,
                                             String itemKey,
                                             String fieldName,
                                             String rawValue,
                                             String message) {
        return ManualFinancialImportError.builder()
                .lineNumber(lineNumber)
                .tickerCode(tickerCode)
                .periodYear(periodYear)
                .reportType(reportType)
                .itemKey(itemKey)
                .fieldName(fieldName)
                .rawValue(rawValue)
                .message(message)
                .build();
    }

    private String normalizeKey(String value) {
        String normalized = blankToNull(value);
        return normalized != null ? normalized.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValidatedRow(CompanyProfile company,
                                Integer sourceLineNumber,
                                Integer periodYear,
                                Integer periodQuarter,
                                ReportType reportType,
                                LocalDate publishedAt,
                                String sourceUrl,
                                FinancialItemKey itemKey,
                                String rawLabel,
                                BigDecimal value,
                                String currency,
                                Integer unitMultiplier,
                                BigDecimal sharesOutstanding,
                                boolean sharesOutstandingProvided) {
    }

    private record ReportKey(Long companyId, Integer periodYear, Integer periodQuarter, ReportType reportType) {
    }

    private record PreparedValueImport(FinancialItemKey itemKey,
                                       String rawLabel,
                                       BigDecimal value,
                                       String currency,
                                       Integer unitMultiplier) {
    }

    private record ImportPreparation(List<PreparedReportImport> reportImports,
                                     int createdReports,
                                     int updatedReports,
                                     int createdValues,
                                     int updatedValues,
                                     int deletedStaleValues,
                                     List<String> affectedTickers,
                                     List<String> preservedRealRatios,
                                     List<String> skippedExisting,
                                     List<ShareCountUpdate> shareCountUpdates,
                                     List<String> updatedShareCounts,
                                     List<String> skippedShareCounts,
                                     List<String> missingShareCountWarnings) {
    }

    private record ShareCountUpdate(CompanyProfile company, BigDecimal sharesOutstanding) {
    }

    private record ImportCounters(int createdReports,
                                  int updatedReports,
                                  int createdValues,
                                  int updatedValues,
                                  int deletedStaleValues) {
    }

    private static final class PreparedReportImport {
        private final CompanyProfile company;
        private final Integer periodYear;
        private final Integer periodQuarter;
        private final ReportType reportType;
        private LocalDate publishedAt;
        private String sourceUrl;
        private final Map<FinancialItemKey, PreparedValueImport> items;
        private CompanyFinancialReport existingReport;
        private Set<String> staleItemKeys = Set.of();

        private PreparedReportImport(CompanyProfile company,
                                     Integer periodYear,
                                     Integer periodQuarter,
                                     ReportType reportType,
                                     LocalDate publishedAt,
                                     String sourceUrl,
                                     Map<FinancialItemKey, PreparedValueImport> items) {
            this.company = company;
            this.periodYear = periodYear;
            this.periodQuarter = periodQuarter;
            this.reportType = reportType;
            this.publishedAt = publishedAt;
            this.sourceUrl = sourceUrl;
            this.items = items;
        }

        public CompanyProfile company() { return company; }
        public Integer periodYear() { return periodYear; }
        public Integer periodQuarter() { return periodQuarter; }
        public ReportType reportType() { return reportType; }
        public LocalDate publishedAt() { return publishedAt; }
        public String sourceUrl() { return sourceUrl; }
        public Map<FinancialItemKey, PreparedValueImport> items() { return items; }
        public CompanyFinancialReport existingReport() { return existingReport; }
        public void setPublishedAt(LocalDate publishedAt) { this.publishedAt = publishedAt; }
        public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
        public void setExistingReport(CompanyFinancialReport existingReport) { this.existingReport = existingReport; }
        public void setStaleItemKeys(Set<String> staleItemKeys) { this.staleItemKeys = staleItemKeys; }
        public Set<String> staleItemKeys() { return staleItemKeys; }
    }

    private static final class ShareCountDecision {
        private final CompanyProfile company;
        private BigDecimal firstValidValue;
        private boolean sawShareColumn;

        private ShareCountDecision(CompanyProfile company) {
            this.company = company;
        }

        public CompanyProfile company() { return company; }
        public BigDecimal firstValidValue() { return firstValidValue; }
        public void setFirstValidValue(BigDecimal firstValidValue) { this.firstValidValue = firstValidValue; }
        public boolean sawShareColumn() { return sawShareColumn; }
        public void setSawShareColumn(boolean sawShareColumn) { this.sawShareColumn = sawShareColumn; }
    }
}



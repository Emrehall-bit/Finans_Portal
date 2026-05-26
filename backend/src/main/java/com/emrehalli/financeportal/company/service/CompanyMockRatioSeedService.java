package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.domain.entity.CompanyRatio;
import com.emrehalli.financeportal.company.dto.response.MockRatioSeedResponse;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import com.emrehalli.financeportal.company.persistence.CompanyRatioRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

@Service
public class CompanyMockRatioSeedService {

    private static final Logger logger = LogManager.getLogger(CompanyMockRatioSeedService.class);

    private final CompanyProfileRepository profileRepository;
    private final CompanyRatioRepository ratioRepository;

    public CompanyMockRatioSeedService(CompanyProfileRepository profileRepository,
                                       CompanyRatioRepository ratioRepository) {
        this.profileRepository = profileRepository;
        this.ratioRepository = ratioRepository;
    }

    @Transactional
    public MockRatioSeedResponse seedMockRatios() {
        List<CompanyProfile> allActive = profileRepository.findByActiveTrue();
        Set<Long> companyIdsWithRatios = new HashSet<>(ratioRepository.findDistinctCompanyIds());

        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (CompanyProfile company : allActive) {
            try {
                if (companyIdsWithRatios.contains(company.getId())) {
                    skipped++;
                    continue;
                }
                CompanyRatio mock = buildMockRatio(company, now);
                ratioRepository.save(mock);
                created++;
                logger.info("mock.ratio.created ticker={}", company.getTickerCode());
            } catch (Exception e) {
                errors.add(company.getTickerCode() + ": " + e.getMessage());
                logger.warn("mock.ratio.failed ticker={} reason={}", company.getTickerCode(), e.getMessage());
            }
        }

        logger.info("mock.ratio.seed.done created={} skipped={} errors={}", created, skipped, errors.size());
        return MockRatioSeedResponse.builder()
                .createdMockRatios(created)
                .skippedExistingRatios(skipped)
                .errors(errors)
                .build();
    }

    private CompanyRatio buildMockRatio(CompanyProfile company, OffsetDateTime calculatedAt) {
        String ticker = company.getTickerCode().toUpperCase(Locale.ROOT);
        long seed = deterministicSeed(ticker);
        Random rng = new Random(seed);

        // ~20% loss-maker (deterministic per ticker)
        boolean isLoss = (Math.abs(seed) % 5 == 0);

        BigDecimal grossMargin    = scale6(0.10 + rng.nextDouble() * 0.60);
        BigDecimal netMargin      = isLoss
                ? scale6(-0.05 - rng.nextDouble() * 0.15)
                : scale6(0.04  + rng.nextDouble() * 0.31);
        BigDecimal roe            = isLoss
                ? scale6(-0.05 - rng.nextDouble() * 0.20)
                : scale6(0.08  + rng.nextDouble() * 0.37);
        BigDecimal roa            = isLoss
                ? scale6(-0.02 - rng.nextDouble() * 0.08)
                : scale6(0.02  + rng.nextDouble() * 0.18);
        BigDecimal debtToEquity   = scale4(0.10 + rng.nextDouble() * 3.40);
        BigDecimal peRatio        = isLoss
                ? scale4(-(3   + rng.nextDouble() * 22))
                : scale4(  5   + rng.nextDouble() * 40);
        BigDecimal pbRatio        = scale4(0.30 + rng.nextDouble() * 4.70);
        BigDecimal revenueGrowth  = scale6(-0.10 + rng.nextDouble() * 0.70);
        BigDecimal netProfGrowth  = scale6(-0.50 + rng.nextDouble() * 2.00);
        BigDecimal assetGrowth    = scale6(-0.05 + rng.nextDouble() * 0.50);
        BigDecimal marketCap      = BigDecimal.valueOf(100_000_000L + (long)(rng.nextDouble() * 49_900_000_000L))
                                              .setScale(2, RoundingMode.HALF_UP);

        String healthLabel        = computeHealthLabel(debtToEquity, netMargin, roe);
        String revenueGrowthLabel = growthLabel(revenueGrowth);
        String netProfGrowthLabel = isLoss ? "Zarara Geçti" : growthLabel(netProfGrowth);
        String assetGrowthLabel   = growthLabel(assetGrowth);

        return CompanyRatio.builder()
                .company(company)
                .report(null)
                .priceAtCalc(null)
                .marketCap(marketCap)
                .peRatio(peRatio)
                .pbRatio(pbRatio)
                .debtToEquity(debtToEquity)
                .grossMargin(grossMargin)
                .netMargin(netMargin)
                .roe(roe)
                .roa(roa)
                .revenueGrowth(revenueGrowth)
                .revenueGrowthLabel(revenueGrowthLabel)
                .netProfitGrowth(netProfGrowth)
                .netProfitGrowthLabel(netProfGrowthLabel)
                .assetGrowth(assetGrowth)
                .assetGrowthLabel(assetGrowthLabel)
                .healthLabel(healthLabel)
                .calculatedAt(calculatedAt)
                .build();
    }

    // Deterministic seed: same ticker → same sequence every run
    private long deterministicSeed(String ticker) {
        long seed = 0;
        for (char c : ticker.toCharArray()) {
            seed = seed * 31 + c;
        }
        return seed;
    }

    private BigDecimal scale4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal scale6(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    // Mirrors CompanyRatioService.computeHealthLabel
    private String computeHealthLabel(BigDecimal debtToEquity, BigDecimal netMargin, BigDecimal roe) {
        BigDecimal debtThreshold   = new BigDecimal("2");
        BigDecimal marginThreshold = new BigDecimal("0.15");
        if (debtToEquity.compareTo(debtThreshold) > 0) return "Borçluluk yüksek";
        if (netMargin.compareTo(marginThreshold) > 0 && roe.compareTo(marginThreshold) > 0) return "Kârlılık güçlü";
        if (netMargin.compareTo(BigDecimal.ZERO) < 0) return "Zarar açıklamış";
        return "Nötr";
    }

    private String growthLabel(BigDecimal growth) {
        double v = growth.doubleValue();
        if (v >= 0.20) return "Güçlü büyüme";
        if (v >= 0.05) return "Ilımlı büyüme";
        if (v >= -0.05) return "Durağan";
        return "Düşüş";
    }
}

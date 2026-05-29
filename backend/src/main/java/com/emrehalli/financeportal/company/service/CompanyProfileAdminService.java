package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.dto.response.BasicProfileSeedError;
import com.emrehalli.financeportal.company.dto.response.BasicProfileSeedResponse;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class CompanyProfileAdminService {

    private static final Map<String, SeedProfile> BASIC_PROFILES = Map.ofEntries(
            Map.entry("AKBNK", new SeedProfile("Akbank T.A.Å.", "BankacÄ±lÄ±k", "BIST_ALL", decimal("5200000000"))),
            Map.entry("AKGRT", new SeedProfile("Aksigorta A.Å.", "Sigorta", "BIST_ALL", null)),
            Map.entry("AKSA", new SeedProfile("Aksa Akrilik Kimya Sanayii A.Å.", "Kimya", "BIST_ALL", decimal("3885000000"))),
            Map.entry("AKSEN", new SeedProfile("Aksa Enerji Ãœretim A.Å.", "Enerji", "BIST_ALL", decimal("1226338236"))),
            Map.entry("ALARK", new SeedProfile("Alarko Holding A.Å.", "Holding", "BIST_ALL", null)),
            Map.entry("ALBRK", new SeedProfile("Albaraka TÃ¼rk KatÄ±lÄ±m BankasÄ± A.Å.", "BankacÄ±lÄ±k", "BIST_ALL", null)),
            Map.entry("ALFAS", new SeedProfile("Alfa Solar Enerji Sanayi ve Ticaret A.Å.", "Enerji", "BIST_ALL", null)),
            Map.entry("ALKIM", new SeedProfile("Alkim Alkali Kimya A.Å.", "Kimya", "BIST_ALL", null)),
            Map.entry("ARCLK", new SeedProfile("ArÃ§elik A.Å.", "DayanÄ±klÄ± TÃ¼ketim", "BIST_ALL", decimal("675728205"))),
            Map.entry("ARDYZ", new SeedProfile("Ard Grup BiliÅŸim Teknolojileri A.Å.", "Teknoloji", "BIST_ALL", null))
    );

    private final CompanyProfileRepository companyProfileRepository;

    public CompanyProfileAdminService(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    @Transactional
    public BasicProfileSeedResponse seedBasicProfiles() {
        int created = 0;
        int skippedExisting = 0;
        List<BasicProfileSeedError> errors = new ArrayList<>();

        for (Map.Entry<String, SeedProfile> entry : BASIC_PROFILES.entrySet()) {
            String ticker = normalizeTicker(entry.getKey());
            SeedProfile seedProfile = entry.getValue();

            try {
                Optional<CompanyProfile> existingOptional = companyProfileRepository.findByTickerCodeIgnoreCase(ticker);
                if (existingOptional.isPresent()) {
                    CompanyProfile existing = existingOptional.get();
                    boolean changed = fillMissingFields(existing, seedProfile);
                    if (changed) {
                        existing.setUpdatedAt(OffsetDateTime.now());
                        companyProfileRepository.save(existing);
                    }
                    skippedExisting++;
                    continue;
                }

                OffsetDateTime now = OffsetDateTime.now();
                CompanyProfile companyProfile = CompanyProfile.builder()
                        .tickerCode(ticker)
                        .companyName(seedProfile.companyName())
                        .sector(seedProfile.sector())
                        .market(seedProfile.market())
                        .sharesOutstanding(seedProfile.sharesOutstanding())
                        .active(true)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                companyProfileRepository.save(companyProfile);
                created++;
            } catch (Exception e) {
                errors.add(BasicProfileSeedError.builder()
                        .ticker(ticker)
                        .message(e.getMessage())
                        .build());
            }
        }

        return BasicProfileSeedResponse.builder()
                .created(created)
                .skippedExisting(skippedExisting)
                .errors(errors)
                .build();
    }

    private boolean fillMissingFields(CompanyProfile existing, SeedProfile seedProfile) {
        boolean changed = false;

        if (isBlank(existing.getCompanyName())) {
            existing.setCompanyName(seedProfile.companyName());
            changed = true;
        }
        if (isBlank(existing.getSector())) {
            existing.setSector(seedProfile.sector());
            changed = true;
        }
        if (isBlank(existing.getMarket())) {
            existing.setMarket(seedProfile.market());
            changed = true;
        }
        if (existing.getSharesOutstanding() == null && seedProfile.sharesOutstanding() != null) {
            existing.setSharesOutstanding(seedProfile.sharesOutstanding());
            changed = true;
        }

        return changed;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private String normalizeTicker(String ticker) {
        return ticker == null ? null : ticker.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SeedProfile(String companyName, String sector, String market, BigDecimal sharesOutstanding) {
    }
}


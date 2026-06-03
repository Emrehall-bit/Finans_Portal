package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.dto.response.BasicProfileSeedError;
import com.emrehalli.financeportal.company.dto.response.BasicProfileSeedResponse;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CompanyProfileAdminService {

    /**
     * Bilinen BIST hisseleri için doğru tam şirket unvanları.
     * seed-all-stock-profiles bu map'i birincil kaynak olarak kullanır;
     * map'te olmayan hisseler için instrument_name'e, o da yoksa ticker_code'a düşülür.
     */
    static final Map<String, String> SEED_NAME_MAP = Map.ofEntries(
            Map.entry("AEFES", "ANADOLU EFES BİRACILIK VE MALT SANAYİİ A.Ş."),
            Map.entry("DSTKF", "DESTEK FİNANS FAKTORİNG A.Ş."),
            Map.entry("EKGYO", "EMLAK KONUT GAYRİMENKUL YATIRIM ORTAKLIĞI A.Ş."),
            Map.entry("TAVHL", "TAV HAVALİMANLARI HOLDİNG A.Ş."),
            Map.entry("TCELL", "TURKCELL İLETİŞİM HİZMETLERİ A.Ş."),
            Map.entry("PGSUS", "PEGASUS HAVA TAŞIMACILIĞI A.Ş."),
            Map.entry("GARAN", "TÜRKİYE GARANTİ BANKASI A.Ş."),
            Map.entry("AKBNK", "AKBANK T.A.Ş."),
            Map.entry("ISCTR", "TÜRKİYE İŞ BANKASI A.Ş."),
            Map.entry("THYAO", "TÜRK HAVA YOLLARI A.O."),
            Map.entry("TUPRS", "TÜPRAŞ-TÜRKİYE PETROL RAFİNERİLERİ A.Ş."),
            Map.entry("KCHOL", "KOÇ HOLDİNG A.Ş."),
            Map.entry("SAHOL", "HACI ÖMER SABANCI HOLDİNG A.Ş."),
            Map.entry("SISE",  "TÜRKİYE ŞİŞE VE CAM FABRİKALARI A.Ş."),
            Map.entry("ASELS", "ASELSAN ELEKTRONİK SANAYİ VE TİCARET A.Ş."),
            Map.entry("BIMAS", "BİM BİRLEŞİK MAĞAZALAR A.Ş.")
    );

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
    private final MarketInstrumentRepository instrumentRepository;

    public CompanyProfileAdminService(CompanyProfileRepository companyProfileRepository,
                                      MarketInstrumentRepository instrumentRepository) {
        this.companyProfileRepository = companyProfileRepository;
        this.instrumentRepository = instrumentRepository;
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

    /**
     * market_instruments tablosundaki tüm STOCK enstrümanları için company_profiles eşler.
     *
     * Yeni profil için ad önceliği:
     *   1. SEED_NAME_MAP → bilinen tam unvan
     *   2. instrument_name (ticker'dan farklıysa)
     *   3. ticker_code (zayıf — sadece KAP import'ta eşleşme sağlamak için yeterli değil)
     *
     * Mevcut zayıf profil (company_name == ticker_code) için:
     *   SEED_NAME_MAP'te varsa company_name güncellenir.
     */
    @Transactional
    public BasicProfileSeedResponse seedFromMarketInstruments() {
        List<MarketInstrument> stocks = instrumentRepository.findAllByInstrumentType(InstrumentType.STOCK);

        // Mevcut profilleri ticker → profile olarak yükle (hem skip hem update için)
        Map<String, CompanyProfile> existingByTicker = companyProfileRepository.findAll().stream()
                .collect(Collectors.toMap(
                        cp -> cp.getTickerCode().toUpperCase(Locale.ROOT),
                        cp -> cp,
                        (a, b) -> a));

        int created = 0;
        int updatedWeak = 0;
        int skippedExisting = 0;
        List<BasicProfileSeedError> errors = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (MarketInstrument instrument : stocks) {
            String ticker = normalizeTicker(instrument.getInstrumentCode());
            if (ticker == null || ticker.isBlank()) continue;

            CompanyProfile existing = existingByTicker.get(ticker);

            if (existing != null) {
                // Mevcut kayıt var — sadece zayıf profil + seed map'te varsa güncelle
                boolean isWeak = existing.getCompanyName() != null
                        && existing.getCompanyName().trim().equalsIgnoreCase(ticker);
                if (isWeak && SEED_NAME_MAP.containsKey(ticker)) {
                    existing.setCompanyName(SEED_NAME_MAP.get(ticker));
                    existing.setUpdatedAt(now);
                    companyProfileRepository.save(existing);
                    updatedWeak++;
                } else {
                    skippedExisting++;
                }
                continue;
            }

            // Yeni profil — en iyi adı seç
            String companyName = resolveCompanyName(ticker, instrument.getInstrumentName());

            try {
                CompanyProfile profile = CompanyProfile.builder()
                        .tickerCode(ticker)
                        .companyName(companyName)
                        .sector("Genel")
                        .market("BIST_ALL")
                        .active(true)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                companyProfileRepository.save(profile);
                existingByTicker.put(ticker, profile);
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
                .updatedWeak(updatedWeak)
                .skippedExisting(skippedExisting)
                .errors(errors)
                .build();
    }

    /**
     * Yeni profil için en iyi company_name:
     * seed map → instrument_name (ticker'dan farklıysa) → ticker_code
     */
    private String resolveCompanyName(String ticker, String instrumentName) {
        if (SEED_NAME_MAP.containsKey(ticker)) {
            return SEED_NAME_MAP.get(ticker);
        }
        if (instrumentName != null && !instrumentName.isBlank()
                && !instrumentName.trim().equalsIgnoreCase(ticker)) {
            return instrumentName;
        }
        return ticker;
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


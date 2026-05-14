package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.company.dto.CompanyDisclosureSyncResponse;
import com.emrehalli.financeportal.company.entity.CompanyDisclosure;
import com.emrehalli.financeportal.company.entity.CompanyProfile;
import com.emrehalli.financeportal.company.provider.kap.KapDisclosureProvider;
import com.emrehalli.financeportal.company.provider.kap.dto.KapDisclosureDto;
import com.emrehalli.financeportal.company.repository.CompanyDisclosureRepository;
import com.emrehalli.financeportal.company.repository.CompanyProfileRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CompanyDisclosureSyncService {

    private static final Logger logger = LogManager.getLogger(CompanyDisclosureSyncService.class);
    private static final long INTER_COMPANY_DELAY_MS = 500;

    private final CompanyProfileRepository profileRepository;
    private final CompanyDisclosureRepository disclosureRepository;
    private final KapDisclosureProvider kapDisclosureProvider;

    public CompanyDisclosureSyncService(CompanyProfileRepository profileRepository,
                                        CompanyDisclosureRepository disclosureRepository,
                                        KapDisclosureProvider kapDisclosureProvider) {
        this.profileRepository = profileRepository;
        this.disclosureRepository = disclosureRepository;
        this.kapDisclosureProvider = kapDisclosureProvider;
    }

    public CompanyDisclosureSyncResponse syncDisclosures(String tickerCode) {
        CompanyProfile company = profileRepository.findByTickerCodeIgnoreCase(tickerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Şirket bulunamadı: " + tickerCode));

        String searchQuery = company.getKapCompanyId() != null && !company.getKapCompanyId().isBlank()
                ? company.getKapCompanyId()
                : company.getTickerCode();

        List<KapDisclosureDto> fetched;
        try {
            fetched = kapDisclosureProvider.fetchDisclosures(searchQuery);
        } catch (Exception e) {
            logger.error("KAP fetch failed. ticker={}, query={}", tickerCode, searchQuery, e);
            return CompanyDisclosureSyncResponse.builder()
                    .tickerCode(tickerCode)
                    .fetchedCount(0)
                    .savedCount(0)
                    .duplicateSkippedCount(0)
                    .failedCount(1)
                    .message("KAP verisi çekilemedi: " + e.getMessage())
                    .build();
        }

        int savedCount = 0;
        int duplicateSkippedCount = 0;
        int failedCount = 0;

        for (KapDisclosureDto dto : fetched) {
            try {
                if (dto.getPublishedAt() == null) {
                    logger.debug("Disclosure skipped — null publishedAt. ticker={}, title={}", tickerCode, dto.getTitle());
                    failedCount++;
                    continue;
                }
                if (isDuplicate(company.getId(), dto)) {
                    duplicateSkippedCount++;
                    continue;
                }
                disclosureRepository.save(CompanyDisclosure.builder()
                        .company(company)
                        .disclosureType(dto.getDisclosureType())
                        .title(dto.getTitle())
                        .kapUrl(dto.getKapUrl())
                        .publishedAt(dto.getPublishedAt())
                        .summary(dto.getSummary())
                        .createdAt(OffsetDateTime.now())
                        .build());
                savedCount++;
            } catch (Exception e) {
                logger.warn("Disclosure save failed. ticker={}, url={}", tickerCode, dto.getKapUrl(), e);
                failedCount++;
            }
        }

        logger.info("KAP disclosure sync done. ticker={}, fetched={}, saved={}, dupes={}, failed={}",
                tickerCode, fetched.size(), savedCount, duplicateSkippedCount, failedCount);

        return CompanyDisclosureSyncResponse.builder()
                .tickerCode(tickerCode)
                .fetchedCount(fetched.size())
                .savedCount(savedCount)
                .duplicateSkippedCount(duplicateSkippedCount)
                .failedCount(failedCount)
                .message("Sync tamamlandı.")
                .build();
    }

    public List<CompanyDisclosureSyncResponse> syncAllDisclosures() {
        List<CompanyProfile> companies = profileRepository.findByActiveTrue();
        List<CompanyDisclosureSyncResponse> results = new ArrayList<>();

        for (int i = 0; i < companies.size(); i++) {
            String ticker = companies.get(i).getTickerCode();
            try {
                results.add(syncDisclosures(ticker));
            } catch (Exception e) {
                logger.error("Sync failed. ticker={}", ticker, e);
                results.add(CompanyDisclosureSyncResponse.builder()
                        .tickerCode(ticker)
                        .fetchedCount(0)
                        .savedCount(0)
                        .duplicateSkippedCount(0)
                        .failedCount(1)
                        .message("Hata: " + e.getMessage())
                        .build());
            }

            if (i < companies.size() - 1) {
                try {
                    Thread.sleep(INTER_COMPANY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("sync-all interrupted after {} companies", i + 1);
                    break;
                }
            }
        }

        return results;
    }

    private boolean isDuplicate(Long companyId, KapDisclosureDto dto) {
        if (dto.getKapUrl() != null && disclosureRepository.existsByKapUrl(dto.getKapUrl())) {
            return true;
        }
        if (dto.getTitle() != null && dto.getPublishedAt() != null) {
            return disclosureRepository.existsByCompanyIdAndTitleAndPublishedAt(
                    companyId, dto.getTitle(), dto.getPublishedAt());
        }
        return false;
    }
}

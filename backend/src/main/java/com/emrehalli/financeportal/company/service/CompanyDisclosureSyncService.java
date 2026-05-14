package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.company.dto.CompanyDisclosureSyncResponse;
import com.emrehalli.financeportal.company.dto.DisclosureFailedItemDto;
import com.emrehalli.financeportal.company.entity.CompanyDisclosure;
import com.emrehalli.financeportal.company.entity.CompanyProfile;
import com.emrehalli.financeportal.company.provider.kap.KapDisclosureProvider;
import com.emrehalli.financeportal.company.provider.kap.KapDisclosureProviderResult;
import com.emrehalli.financeportal.company.provider.kap.dto.KapDisclosureDto;
import com.emrehalli.financeportal.company.repository.CompanyDisclosureRepository;
import com.emrehalli.financeportal.company.repository.CompanyProfileRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    @Transactional
    public CompanyDisclosureSyncResponse syncDisclosures(String tickerCode) {
        CompanyProfile company = profileRepository.findByTickerCodeIgnoreCase(tickerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Şirket bulunamadı: " + tickerCode));

        if (company.getMkkMemberOid() == null || company.getMkkMemberOid().isBlank()) {
            logger.warn("mkkMemberOid missing for ticker={}", tickerCode);
            return CompanyDisclosureSyncResponse.builder()
                    .tickerCode(tickerCode)
                    .fetchedCount(0)
                    .savedCount(0)
                    .updatedCount(0)
                    .duplicateSkippedCount(0)
                    .failedCount(0)
                    .failedItems(List.of())
                    .message("mkkMemberOid missing for ticker " + tickerCode)
                    .build();
        }

        logger.info("KAP disclosure fetch started. ticker={}, mkkMemberOid={}", tickerCode, company.getMkkMemberOid());

        KapDisclosureProviderResult providerResult;
        try {
            providerResult = kapDisclosureProvider.fetchDisclosures(company.getMkkMemberOid());
        } catch (Exception e) {
            logger.error("KAP fetch failed. ticker={}, mkkMemberOid={}", tickerCode, company.getMkkMemberOid(), e);
            return CompanyDisclosureSyncResponse.builder()
                    .tickerCode(tickerCode)
                    .fetchedCount(0)
                    .savedCount(0)
                    .updatedCount(0)
                    .duplicateSkippedCount(0)
                    .failedCount(1)
                    .failedItems(List.of())
                    .message("KAP verisi çekilemedi: " + e.getMessage())
                    .build();
        }

        List<KapDisclosureDto> fetched = providerResult.disclosures();
        List<DisclosureFailedItemDto> allFailedItems = new ArrayList<>(providerResult.failedItems());

        int savedCount = 0;
        int updatedCount = 0;
        int duplicateSkippedCount = 0;

        for (KapDisclosureDto dto : fetched) {
            try {
                if (dto.getKapUrl() != null) {
                    Optional<CompanyDisclosure> existing = disclosureRepository.findByKapUrl(dto.getKapUrl());
                    if (existing.isPresent()) {
                        if (applyUpdates(existing.get(), dto)) {
                            disclosureRepository.save(existing.get());
                            updatedCount++;
                        } else {
                            duplicateSkippedCount++;
                        }
                        continue;
                    }
                } else if (dto.getTitle() != null && dto.getPublishedAt() != null
                        && disclosureRepository.existsByCompanyIdAndTitleAndPublishedAt(
                                company.getId(), dto.getTitle(), dto.getPublishedAt())) {
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
                        .kapYear(dto.getKapYear())
                        .kapDonem(dto.getKapDonem())
                        .kapPeriod(dto.getKapPeriod())
                        .createdAt(OffsetDateTime.now())
                        .build());
                savedCount++;

            } catch (Exception e) {
                logger.warn("Disclosure upsert failed. ticker={}, disclosureIndex={}",
                        tickerCode, dto.getDisclosureIndex(), e);
                allFailedItems.add(DisclosureFailedItemDto.builder()
                        .title(dto.getTitle())
                        .disclosureIndex(dto.getDisclosureIndex())
                        .reason("Kaydetme hatası: " + e.getMessage())
                        .build());
            }
        }

        int totalFetched = fetched.size() + providerResult.failedItems().size();

        logger.info("KAP disclosure sync done. ticker={}, fetched={}, saved={}, updated={}, dupes={}, failed={}",
                tickerCode, totalFetched, savedCount, updatedCount, duplicateSkippedCount, allFailedItems.size());

        return CompanyDisclosureSyncResponse.builder()
                .tickerCode(tickerCode)
                .fetchedCount(totalFetched)
                .savedCount(savedCount)
                .updatedCount(updatedCount)
                .duplicateSkippedCount(duplicateSkippedCount)
                .failedCount(allFailedItems.size())
                .failedItems(allFailedItems.isEmpty() ? null : allFailedItems)
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
                        .updatedCount(0)
                        .duplicateSkippedCount(0)
                        .failedCount(1)
                        .failedItems(null)
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

    /**
     * Updates mutable fields on an existing disclosure from the incoming DTO.
     * Returns true if at least one field changed (caller should persist), false if nothing changed.
     */
    private boolean applyUpdates(CompanyDisclosure existing, KapDisclosureDto dto) {
        boolean changed = false;

        if (!Objects.equals(existing.getTitle(), dto.getTitle())) {
            existing.setTitle(dto.getTitle());
            changed = true;
        }
        if (!Objects.equals(existing.getSummary(), dto.getSummary())) {
            existing.setSummary(dto.getSummary());
            changed = true;
        }
        if (!Objects.equals(existing.getDisclosureType(), dto.getDisclosureType())) {
            existing.setDisclosureType(dto.getDisclosureType());
            changed = true;
        }
        if (!Objects.equals(existing.getPublishedAt(), dto.getPublishedAt())) {
            existing.setPublishedAt(dto.getPublishedAt());
            changed = true;
        }
        if (!Objects.equals(existing.getKapYear(), dto.getKapYear())) {
            existing.setKapYear(dto.getKapYear());
            changed = true;
        }
        if (!Objects.equals(existing.getKapDonem(), dto.getKapDonem())) {
            existing.setKapDonem(dto.getKapDonem());
            changed = true;
        }
        if (!Objects.equals(existing.getKapPeriod(), dto.getKapPeriod())) {
            existing.setKapPeriod(dto.getKapPeriod());
            changed = true;
        }

        return changed;
    }
}

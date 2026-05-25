package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import com.emrehalli.financeportal.market.provider.bond.dto.BondRateDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for bond persistence and retrieval.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BondService {

    private static final String META_DELIMITER = "||META||";

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final CacheService cacheService;
    private final EntityManager entityManager;
    private final MarketProperties props;

    @Transactional
    public void saveAll(List<BondRateDto> rates) {
        if (rates == null || rates.isEmpty()) {
            return;
        }

        for (BondRateDto rate : rates) {
            if (rate == null
                    || isBlank(rate.getBondCode())
                    || rate.getInterestRate() == null) {
                continue;
            }

            String bondCode = normalizeCode(rate.getBondCode());
            LocalDateTime timestamp = rate.getDataTimestamp() != null ? rate.getDataTimestamp() : LocalDateTime.now();
            String bondName = defaultString(rate.getBondName(), bondCode);

            MarketInstrument instrument = marketInstrumentRepository.findByInstrumentCodeAndSourceName(bondCode, SourceName.TCMB)
                    .orElseGet(() -> marketInstrumentRepository.save(MarketInstrument.builder()
                            .instrumentCode(bondCode)
                            .instrumentName(encodeInstrumentName(bondName, rate.getMaturityDate()))
                            .instrumentType(InstrumentType.BOND)
                            .sourceName(SourceName.TCMB)
                            .build()));

            instrument.setInstrumentName(encodeInstrumentName(bondName, rate.getMaturityDate()));
            marketInstrumentRepository.save(instrument);

            marketPriceRepository.save(MarketPrice.builder()
                    .instrument(instrument)
                    .priceValue(rate.getInterestRate())
                    .priceTimestamp(timestamp)
                    .sourceName(SourceName.TCMB)
                    .build());

            putCacheSilently(buildCacheKey(bondCode), toDto(instrument, rate.getInterestRate(), timestamp));
        }
    }

    @Transactional(readOnly = true)
    public Page<BondRateDto> getAll(Pageable pageable) {
        try {
            TypedQuery<MarketInstrument> dataQuery = entityManager.createQuery(
                    "select mi from MarketInstrument mi " +
                            "where mi.instrumentType = :instrumentType " +
                            "and mi.sourceName = :sourceName " +
                            "order by mi.instrumentCode asc",
                    MarketInstrument.class
            );
            dataQuery.setParameter("instrumentType", InstrumentType.BOND);
            dataQuery.setParameter("sourceName", SourceName.TCMB);
            dataQuery.setFirstResult((int) pageable.getOffset());
            dataQuery.setMaxResults(pageable.getPageSize());

            List<BondRateDto> content = dataQuery.getResultList().stream()
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .toList();

            Long total = entityManager.createQuery(
                            "select count(mi) from MarketInstrument mi " +
                                    "where mi.instrumentType = :instrumentType " +
                                    "and mi.sourceName = :sourceName",
                            Long.class
                    )
                    .setParameter("instrumentType", InstrumentType.BOND)
                    .setParameter("sourceName", SourceName.TCMB)
                    .getSingleResult();

            return new PageImpl<>(content, pageable, total);
        } catch (Exception exception) {
            log.error("Failed to load paged bond data from database.", exception);
            return Page.empty(pageable);
        }
    }

    @Transactional(readOnly = true)
    public BondRateDto getByCode(String bondCode) {
        String normalizedCode = normalizeCode(bondCode);
        BondRateDto cached = getCachedBond(normalizedCode);
        if (cached != null) {
            return cached;
        }

        try {
            BondRateDto dto = marketInstrumentRepository.findByInstrumentCodeAndSourceName(normalizedCode, SourceName.TCMB)
                    .filter(instrument -> instrument.getInstrumentType() == InstrumentType.BOND)
                    .map(this::toDto)
                    .orElse(null);
            if (dto != null) {
                putCacheSilently(buildCacheKey(normalizedCode), dto);
            }
            return dto;
        } catch (Exception exception) {
            log.error("Failed to load bond data for code {}. Returning cached value when available.", normalizedCode, exception);
            return getCachedBond(normalizedCode);
        }
    }

    private BondRateDto toDto(MarketInstrument instrument) {
        Optional<MarketPrice> latestPrice = marketPriceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument);
        if (latestPrice.isEmpty()) {
            return null;
        }
        MarketPrice price = latestPrice.get();
        return toDto(instrument, price.getPriceValue(), price.getPriceTimestamp());
    }

    private BondRateDto toDto(MarketInstrument instrument, BigDecimal interestRate, LocalDateTime dataTimestamp) {
        BondMetadata metadata = decodeInstrumentName(instrument.getInstrumentName());
        return BondRateDto.builder()
                .bondCode(instrument.getInstrumentCode())
                .bondName(metadata.bondName())
                .interestRate(interestRate)
                .maturityDate(metadata.maturityDate())
                .dataTimestamp(dataTimestamp)
                .build();
    }

    private BondRateDto getCachedBond(String bondCode) {
        try {
            return cacheService.get(buildCacheKey(bondCode), BondRateDto.class).orElse(null);
        } catch (Exception exception) {
            log.warn("Failed to read bond cache for code={}", bondCode, exception);
        }
        return null;
    }

    private void putCacheSilently(String key, Object value) {
        try {
            cacheService.put(key, value, props.getCache().getTtlMinutes().getBond());
        } catch (Exception exception) {
            log.warn("Failed to write bond cache for key={}", key, exception);
        }
    }

    private String buildCacheKey(String bondCode) {
        return "bond:" + bondCode;
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String encodeInstrumentName(String bondName, LocalDate maturityDate) {
        return bondName.trim() + META_DELIMITER + (maturityDate != null ? maturityDate : "");
    }

    private BondMetadata decodeInstrumentName(String instrumentName) {
        if (instrumentName == null || instrumentName.isBlank()) {
            return new BondMetadata("", null);
        }

        String[] parts = instrumentName.split("\\Q" + META_DELIMITER + "\\E", 2);
        String bondName = parts.length > 0 ? parts[0] : "";

        LocalDate maturityDate = null;
        if (parts.length > 1 && !parts[1].isBlank()) {
            try {
                maturityDate = LocalDate.parse(parts[1]);
            } catch (Exception ignored) {
                // Ignore malformed metadata.
            }
        }

        return new BondMetadata(bondName, maturityDate);
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record BondMetadata(String bondName, LocalDate maturityDate) {
    }
}




package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import com.emrehalli.financeportal.market.provider.fund.dto.FundNavDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for fund persistence and retrieval.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FundService {

    private static final String CACHE_KEY_ALL = "fund:all";
    private static final String TYPE_DELIMITER = "||TYPE||";

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final CacheService cacheService;
    private final EntityManager entityManager;
    private final MarketProperties props;

    @Transactional
    public void saveAll(List<FundNavDto> funds) {
        if (funds == null || funds.isEmpty()) {
            return;
        }

        for (FundNavDto fund : funds) {
            if (fund == null
                    || isBlank(fund.getFundCode())
                    || isBlank(fund.getFundName())
                    || fund.getNavValue() == null) {
                continue;
            }

            String fundCode = normalizeCode(fund.getFundCode());
            String fundType = normalizeType(fund.getFundType());
            LocalDate navDate = fund.getNavDate() != null ? fund.getNavDate() : LocalDate.now();
            LocalDateTime priceTimestamp = navDate.atStartOfDay();

            MarketInstrument instrument = marketInstrumentRepository.findByInstrumentCodeAndSourceName(fundCode, SourceName.TEFAS)
                    .orElseGet(() -> marketInstrumentRepository.save(MarketInstrument.builder()
                            .instrumentCode(fundCode)
                            .instrumentName(encodeInstrumentName(fund.getFundName(), fundType))
                            .instrumentType(InstrumentType.FUND)
                            .sourceName(SourceName.TEFAS)
                            .build()));

            instrument.setInstrumentName(encodeInstrumentName(fund.getFundName(), fundType));
            marketInstrumentRepository.save(instrument);

            marketPriceRepository.save(MarketPrice.builder()
                    .instrument(instrument)
                    .priceValue(fund.getNavValue())
                    .priceTimestamp(priceTimestamp)
                    .sourceName(SourceName.TEFAS)
                    .build());

            putCacheSilently(buildCacheKey(fundCode), toResponse(instrument, fund.getNavValue(), priceTimestamp));
        }

        evictCacheSilently(CACHE_KEY_ALL);
    }

    @Transactional(readOnly = true)
    public List<FundNavDto> getAll() {
        List<FundNavDto> cached = cacheService.getList(CACHE_KEY_ALL, FundNavDto.class);
        if (!cached.isEmpty()) {
            return cached;
        }

        try {
            List<FundNavDto> funds = getFundInstruments().stream()
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(FundNavDto::getFundCode))
                    .toList();
            putCacheSilently(CACHE_KEY_ALL, funds);
            return funds;
        } catch (Exception exception) {
            log.error("Failed to load all funds from database. Returning cached values when available.", exception);
            return cacheService.getList(CACHE_KEY_ALL, FundNavDto.class);
        }
    }

    @Transactional(readOnly = true)
    public FundNavDto getByCode(String code) {
        String normalizedCode = normalizeCode(code);
        FundNavDto cached = getCachedFund(normalizedCode);
        if (cached != null) {
            return cached;
        }

        try {
            FundNavDto fund = marketInstrumentRepository.findByInstrumentCodeAndSourceName(normalizedCode, SourceName.TEFAS)
                    .filter(instrument -> instrument.getInstrumentType() == InstrumentType.FUND)
                    .map(this::toDto)
                    .orElse(null);
            if (fund != null) {
                putCacheSilently(buildCacheKey(normalizedCode), fund);
            }
            return fund;
        } catch (Exception exception) {
            log.error("Failed to load fund {} from database. Returning cached value when available.", normalizedCode, exception);
            return getCachedFund(normalizedCode);
        }
    }

    @Transactional(readOnly = true)
    public List<FundNavDto> getByType(String fundType) {
        String normalizedType = normalizeType(fundType);
        try {
            TypedQuery<MarketInstrument> query = entityManager.createQuery(
                    "select mi from MarketInstrument mi " +
                            "where mi.instrumentType = :instrumentType " +
                            "and mi.sourceName = :sourceName " +
                            "and upper(mi.instrumentName) like :typePattern",
                    MarketInstrument.class
            );
            query.setParameter("instrumentType", InstrumentType.FUND);
            query.setParameter("sourceName", SourceName.TEFAS);
            query.setParameter("typePattern", "%"+ TYPE_DELIMITER + normalizedType);

            return query.getResultList().stream()
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(FundNavDto::getFundCode))
                    .toList();
        } catch (Exception exception) {
            log.error("Failed to load funds for type {} from database.", normalizedType, exception);
            return List.of();
        }
    }

    private List<MarketInstrument> getFundInstruments() {
        return marketInstrumentRepository.findAll().stream()
                .filter(instrument -> instrument.getInstrumentType() == InstrumentType.FUND)
                .filter(instrument -> instrument.getSourceName() == SourceName.TEFAS)
                .toList();
    }

    private FundNavDto toDto(MarketInstrument instrument) {
        Optional<MarketPrice> latestPrice = marketPriceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument);
        if (latestPrice.isEmpty()) {
            return null;
        }

        MarketPrice price = latestPrice.get();
        FundMetadata metadata = decodeInstrumentName(instrument.getInstrumentName());
        return toResponse(instrument, metadata.fundName(), metadata.fundType(), price.getPriceValue(), price.getPriceTimestamp());
    }

    private FundNavDto toResponse(MarketInstrument instrument, java.math.BigDecimal navValue, LocalDateTime priceTimestamp) {
        FundMetadata metadata = decodeInstrumentName(instrument.getInstrumentName());
        return toResponse(instrument, metadata.fundName(), metadata.fundType(), navValue, priceTimestamp);
    }

    private FundNavDto toResponse(MarketInstrument instrument,
                                  String fundName,
                                  String fundType,
                                  java.math.BigDecimal navValue,
                                  LocalDateTime priceTimestamp) {
        return FundNavDto.builder()
                .fundCode(instrument.getInstrumentCode())
                .fundName(fundName)
                .navValue(navValue)
                .navDate(priceTimestamp != null ? priceTimestamp.toLocalDate() : null)
                .fundType(fundType)
                .build();
    }

    private FundNavDto getCachedFund(String code) {
        try {
            return cacheService.get(buildCacheKey(code), FundNavDto.class).orElse(null);
        } catch (Exception exception) {
            log.warn("Failed to read fund cache for code={}", code, exception);
        }
        return null;
    }

    private void putCacheSilently(String key, Object value) {
        try {
            cacheService.put(key, value, props.getCache().getTtlMinutes().getFund());
        } catch (Exception exception) {
            log.warn("Failed to write fund cache for key={}", key, exception);
        }
    }

    private void evictCacheSilently(String key) {
        try {
            cacheService.evict(key);
        } catch (Exception exception) {
            log.warn("Failed to evict fund cache for key={}", key, exception);
        }
    }

    private String buildCacheKey(String code) {
        return "fund:" + code + ":NAV";
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeType(String fundType) {
        if (fundType == null || fundType.isBlank()) {
            return "UNKNOWN";
        }
        return fundType.trim().toUpperCase(Locale.ROOT);
    }

    private String encodeInstrumentName(String fundName, String fundType) {
        return fundName.trim() + TYPE_DELIMITER + fundType;
    }

    private FundMetadata decodeInstrumentName(String instrumentName) {
        if (instrumentName == null || instrumentName.isBlank()) {
            return new FundMetadata("", "UNKNOWN");
        }

        String[] parts = instrumentName.split("\\Q" + TYPE_DELIMITER + "\\E", 2);
        if (parts.length == 2) {
            return new FundMetadata(parts[0], parts[1]);
        }
        return new FundMetadata(instrumentName, "UNKNOWN");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record FundMetadata(String fundName, String fundType) {
    }
}

package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import com.emrehalli.financeportal.market.provider.futures.dto.FuturesContractDto;
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
 * Service for futures persistence and retrieval.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FuturesService {

    private static final String META_DELIMITER = "||META||";

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final CacheService cacheService;
    private final EntityManager entityManager;
    private final MarketProperties props;

    @Transactional
    public void saveAll(List<FuturesContractDto> contracts) {
        if (contracts == null || contracts.isEmpty()) {
            return;
        }

        for (FuturesContractDto contract : contracts) {
            if (contract == null
                    || isBlank(contract.getContractCode())
                    || contract.getLastPrice() == null) {
                continue;
            }

            String contractCode = normalizeCode(contract.getContractCode());
            LocalDateTime timestamp = contract.getDataTimestamp() != null ? contract.getDataTimestamp() : LocalDateTime.now();
            String contractName = defaultString(contract.getContractName(), contractCode);

            MarketInstrument instrument = marketInstrumentRepository.findByInstrumentCodeAndSourceName(contractCode, SourceName.BIST)
                    .orElseGet(() -> marketInstrumentRepository.save(MarketInstrument.builder()
                            .instrumentCode(contractCode)
                            .instrumentName(encodeInstrumentName(contractName, contract.getDailyChangePercent(), contract.getExpiryDate()))
                            .instrumentType(InstrumentType.FUTURES)
                            .sourceName(SourceName.BIST)
                            .build()));

            instrument.setInstrumentName(encodeInstrumentName(contractName, contract.getDailyChangePercent(), contract.getExpiryDate()));
            marketInstrumentRepository.save(instrument);

            marketPriceRepository.save(MarketPrice.builder()
                    .instrument(instrument)
                    .priceValue(contract.getLastPrice())
                    .priceTimestamp(timestamp)
                    .sourceName(SourceName.BIST)
                    .build());

            putCacheSilently(buildCacheKey(contractCode), toDto(instrument, contract.getLastPrice(), timestamp));
        }
    }

    @Transactional(readOnly = true)
    public Page<FuturesContractDto> getAll(Pageable pageable) {
        try {
            TypedQuery<MarketInstrument> dataQuery = entityManager.createQuery(
                    "select mi from MarketInstrument mi " +
                            "where mi.instrumentType = :instrumentType " +
                            "and mi.sourceName = :sourceName " +
                            "order by mi.instrumentCode asc",
                    MarketInstrument.class
            );
            dataQuery.setParameter("instrumentType", InstrumentType.FUTURES);
            dataQuery.setParameter("sourceName", SourceName.BIST);
            dataQuery.setFirstResult((int) pageable.getOffset());
            dataQuery.setMaxResults(pageable.getPageSize());

            List<FuturesContractDto> content = dataQuery.getResultList().stream()
                    .map(this::toDto)
                    .filter(Objects::nonNull)
                    .toList();

            Long total = entityManager.createQuery(
                            "select count(mi) from MarketInstrument mi " +
                                    "where mi.instrumentType = :instrumentType " +
                                    "and mi.sourceName = :sourceName",
                            Long.class
                    )
                    .setParameter("instrumentType", InstrumentType.FUTURES)
                    .setParameter("sourceName", SourceName.BIST)
                    .getSingleResult();

            return new PageImpl<>(content, pageable, total);
        } catch (Exception exception) {
            log.error("Failed to load paged futures data from database.", exception);
            return Page.empty(pageable);
        }
    }

    @Transactional(readOnly = true)
    public FuturesContractDto getByContract(String contractCode) {
        String normalizedCode = normalizeCode(contractCode);
        FuturesContractDto cached = getCachedContract(normalizedCode);
        if (cached != null) {
            return cached;
        }

        try {
            FuturesContractDto dto = marketInstrumentRepository.findByInstrumentCodeAndSourceName(normalizedCode, SourceName.BIST)
                    .filter(instrument -> instrument.getInstrumentType() == InstrumentType.FUTURES)
                    .map(this::toDto)
                    .orElse(null);
            if (dto != null) {
                putCacheSilently(buildCacheKey(normalizedCode), dto);
            }
            return dto;
        } catch (Exception exception) {
            log.error("Failed to load futures data for contract {}. Returning cached value when available.", normalizedCode, exception);
            return getCachedContract(normalizedCode);
        }
    }

    private FuturesContractDto toDto(MarketInstrument instrument) {
        Optional<MarketPrice> latestPrice = marketPriceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument);
        if (latestPrice.isEmpty()) {
            return null;
        }
        MarketPrice price = latestPrice.get();
        return toDto(instrument, price.getPriceValue(), price.getPriceTimestamp());
    }

    private FuturesContractDto toDto(MarketInstrument instrument, BigDecimal lastPrice, LocalDateTime dataTimestamp) {
        FuturesMetadata metadata = decodeInstrumentName(instrument.getInstrumentName());
        return FuturesContractDto.builder()
                .contractCode(instrument.getInstrumentCode())
                .contractName(metadata.contractName())
                .lastPrice(lastPrice)
                .dailyChangePercent(metadata.dailyChangePercent())
                .expiryDate(metadata.expiryDate())
                .dataTimestamp(dataTimestamp)
                .build();
    }

    private FuturesContractDto getCachedContract(String contractCode) {
        try {
            return cacheService.get(buildCacheKey(contractCode), FuturesContractDto.class).orElse(null);
        } catch (Exception exception) {
            log.warn("Failed to read futures cache for contract={}", contractCode, exception);
        }
        return null;
    }

    private void putCacheSilently(String key, Object value) {
        try {
            cacheService.put(key, value, props.getCache().getTtlMinutes().getFutures());
        } catch (Exception exception) {
            log.warn("Failed to write futures cache for key={}", key, exception);
        }
    }

    private String buildCacheKey(String contractCode) {
        return "futures:" + contractCode;
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String encodeInstrumentName(String contractName, BigDecimal dailyChangePercent, LocalDate expiryDate) {
        return contractName.trim()
                + META_DELIMITER
                + (dailyChangePercent != null ? dailyChangePercent.toPlainString() : "")
                + META_DELIMITER
                + (expiryDate != null ? expiryDate : "");
    }

    private FuturesMetadata decodeInstrumentName(String instrumentName) {
        if (instrumentName == null || instrumentName.isBlank()) {
            return new FuturesMetadata("", null, null);
        }

        String[] parts = instrumentName.split("\\Q" + META_DELIMITER + "\\E", 3);
        String contractName = parts.length > 0 ? parts[0] : "";

        BigDecimal dailyChangePercent = null;
        if (parts.length > 1 && !parts[1].isBlank()) {
            try {
                dailyChangePercent = new BigDecimal(parts[1]);
            } catch (NumberFormatException ignored) {
                // Ignore malformed metadata.
            }
        }

        LocalDate expiryDate = null;
        if (parts.length > 2 && !parts[2].isBlank()) {
            try {
                expiryDate = LocalDate.parse(parts[2]);
            } catch (Exception ignored) {
                // Ignore malformed metadata.
            }
        }

        return new FuturesMetadata(contractName, dailyChangePercent, expiryDate);
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record FuturesMetadata(String contractName, BigDecimal dailyChangePercent, LocalDate expiryDate) {
    }
}

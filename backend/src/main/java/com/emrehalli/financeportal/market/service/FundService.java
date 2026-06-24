package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.config.MarketProperties;
import com.emrehalli.financeportal.market.cache.CacheService;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPrice;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import com.emrehalli.financeportal.market.provider.fund.dto.FundNavDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final CacheService cacheService;
    private final EntityManager entityManager;
    private final MarketProperties props;

    @Transactional
    public void saveAll(List<FundNavDto> funds) {
        if (funds == null || funds.isEmpty()) {
            return;
        }

        List<FundNavDto> cachedFunds = new ArrayList<>();
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

            Instant todayStart = LocalDate.now().atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            BigDecimal previousNavValue = marketPriceHistoryRepository
                    .findTopByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampLessThanOrderByPriceTimestampDesc(
                            instrument,
                            IntervalType.ONE_DAY,
                            SourceName.TEFAS,
                            todayStart
                    )
                    .map(MarketPriceHistory::getClosePrice)
                    .orElse(null);
            fund.setPreviousNavValue(previousNavValue);
            BigDecimal changeRate = calculateChangeRate(fund.getNavValue(), previousNavValue);

            marketPriceRepository.save(MarketPrice.builder()
                    .instrument(instrument)
                    .priceValue(fund.getNavValue())
                    .changeRate(changeRate)
                    .priceTimestamp(priceTimestamp)
                    .sourceName(SourceName.TEFAS)
                    .build());

            marketPriceHistoryRepository
                    .findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestamp(
                            instrument,
                            IntervalType.ONE_DAY,
                            SourceName.TEFAS,
                            todayStart
                    )
                    .orElseGet(() -> marketPriceHistoryRepository.save(MarketPriceHistory.builder()
                            .instrument(instrument)
                            .intervalType(IntervalType.ONE_DAY)
                            .sourceName(SourceName.TEFAS)
                            .closePrice(fund.getNavValue())
                            .priceTimestamp(todayStart)
                            .build()));

            FundNavDto response = toResponse(instrument, fund.getNavValue(), previousNavValue, priceTimestamp);
            applyEnrichedFundFields(response, fund);
            putCacheSilently(buildCacheKey(fundCode), response);
            cachedFunds.add(response);
        }

        List<FundNavDto> sortedFunds = cachedFunds.stream()
                .sorted(Comparator.comparing(FundNavDto::getFundCode))
                .toList();
        putCacheSilently(CACHE_KEY_ALL, sortedFunds);
    }

    @Transactional(readOnly = true)
    public List<FundNavDto> getAll() {
        List<FundNavDto> cached = cacheService.getList(CACHE_KEY_ALL, FundNavDto.class);
        if (!cached.isEmpty()) {
            return cached;
        }

        try {
            List<FundNavDto> funds = buildFundDtos(
                    marketInstrumentRepository.findAllByInstrumentTypeAndSourceName(
                            InstrumentType.FUND,
                            SourceName.TEFAS
                    )
            );
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
        List<FundNavDto> cachedFunds = cacheService.getList(CACHE_KEY_ALL, FundNavDto.class);
        if (!cachedFunds.isEmpty()) {
            return cachedFunds.stream()
                    .filter(Objects::nonNull)
                    .filter(fund -> normalizedType.equals(normalizeType(resolveFundTypeForFilter(fund))))
                    .sorted(Comparator.comparing(FundNavDto::getFundCode))
                    .toList();
        }

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

            return buildFundDtos(query.getResultList());
        } catch (Exception exception) {
            log.error("Failed to load funds for type {} from database.", normalizedType, exception);
            return List.of();
        }
    }

    private List<FundNavDto> buildFundDtos(List<MarketInstrument> instruments) {
        if (instruments == null || instruments.isEmpty()) {
            return List.of();
        }

        List<Long> instrumentIds = instruments.stream()
                .map(MarketInstrument::getId)
                .filter(Objects::nonNull)
                .toList();

        if (instrumentIds.isEmpty()) {
            return List.of();
        }

        Map<Long, MarketPrice> latestPricesByInstrumentId = marketPriceRepository
                .findLatestPricesForInstruments(instrumentIds)
                .stream()
                .filter(price -> price.getInstrument() != null && price.getInstrument().getId() != null)
                .collect(Collectors.toMap(
                        price -> price.getInstrument().getId(),
                        Function.identity(),
                        (left, right) -> left
                ));

        Map<Long, MarketPriceHistory> previousClosesByInstrumentId = marketPriceHistoryRepository
                .findPreviousClosesForInstruments(instrumentIds)
                .stream()
                .filter(history -> history.getInstrument() != null && history.getInstrument().getId() != null)
                .collect(Collectors.toMap(
                        history -> history.getInstrument().getId(),
                        Function.identity(),
                        (left, right) -> left
                ));

        return instruments.stream()
                .map(instrument -> toDto(
                        instrument,
                        latestPricesByInstrumentId.get(instrument.getId()),
                        previousClosesByInstrumentId.get(instrument.getId())
                ))
                .map(this::mergeCachedFundMetadata)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FundNavDto::getFundCode))
                .toList();
    }

    private FundNavDto toDto(MarketInstrument instrument) {
        Optional<MarketPrice> latestPrice = marketPriceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument);
        if (latestPrice.isEmpty()) {
            return null;
        }

        MarketPrice price = latestPrice.get();
        FundMetadata metadata = decodeInstrumentName(instrument.getInstrumentName());
        BigDecimal previousNavValue = marketPriceHistoryRepository
                .findTopByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampLessThanOrderByPriceTimestampDesc(
                        instrument,
                        IntervalType.ONE_DAY,
                        SourceName.TEFAS,
                        price.getPriceTimestamp().toLocalDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
                )
                .map(MarketPriceHistory::getClosePrice)
                .orElse(null);
        return mergeCachedFundMetadata(toResponse(
                instrument,
                metadata.fundName(),
                metadata.fundType(),
                price.getPriceValue(),
                previousNavValue,
                price.getPriceTimestamp()
        ));
    }

    private FundNavDto toDto(
            MarketInstrument instrument,
            MarketPrice latestPrice,
            MarketPriceHistory previousClose
    ) {
        if (instrument == null || latestPrice == null) {
            return null;
        }

        FundMetadata metadata = decodeInstrumentName(instrument.getInstrumentName());
        return mergeCachedFundMetadata(toResponse(
                instrument,
                metadata.fundName(),
                metadata.fundType(),
                latestPrice.getPriceValue(),
                previousClose != null ? previousClose.getClosePrice() : null,
                latestPrice.getPriceTimestamp()
        ));
    }

    private FundNavDto toResponse(MarketInstrument instrument, java.math.BigDecimal navValue, BigDecimal previousNavValue, LocalDateTime priceTimestamp) {
        FundMetadata metadata = decodeInstrumentName(instrument.getInstrumentName());
        return toResponse(instrument, metadata.fundName(), metadata.fundType(), navValue, previousNavValue, priceTimestamp);
    }

    private FundNavDto toResponse(MarketInstrument instrument,
                                  String fundName,
                                  String fundType,
                                  BigDecimal navValue,
                                  BigDecimal previousNavValue,
                                  LocalDateTime priceTimestamp) {
        return FundNavDto.builder()
                .fundCode(instrument.getInstrumentCode())
                .fundName(fundName)
                .navValue(navValue)
                .previousNavValue(previousNavValue)
                .navDate(priceTimestamp != null ? priceTimestamp.toLocalDate() : null)
                .fundType(fundType)
                .fonTurAciklama(fundType)
                .build();
    }

    private FundNavDto mergeCachedFundMetadata(FundNavDto fund) {
        if (fund == null || isBlank(fund.getFundCode())) {
            return fund;
        }

        FundNavDto cachedFund = getCachedFund(fund.getFundCode());
        if (cachedFund == null) {
            if (isBlank(fund.getFonTurAciklama())) {
                fund.setFonTurAciklama(fund.getFundType());
            }
            return fund;
        }

        applyEnrichedFundFields(fund, cachedFund);
        return fund;
    }

    private void applyEnrichedFundFields(FundNavDto target, FundNavDto source) {
        if (target == null || source == null) {
            return;
        }

        target.setGetiri1a(source.getGetiri1a());
        target.setGetiri3a(source.getGetiri3a());
        target.setGetiri6a(source.getGetiri6a());
        target.setGetiriYb(source.getGetiriYb());
        target.setGetiri1y(source.getGetiri1y());
        target.setGetiri3y(source.getGetiri3y());
        target.setGetiri5y(source.getGetiri5y());
        target.setRiskDegeri(source.getRiskDegeri());
        target.setFonTurAciklama(isBlank(source.getFonTurAciklama()) ? source.getFundType() : source.getFonTurAciklama());
    }

    private BigDecimal calculateChangeRate(BigDecimal currentNavValue, BigDecimal previousNavValue) {
        if (currentNavValue == null || previousNavValue == null || previousNavValue.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return currentNavValue
                .subtract(previousNavValue)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousNavValue, 4, RoundingMode.HALF_UP);
    }

    private String resolveFundTypeForFilter(FundNavDto fund) {
        if (fund == null) {
            return "";
        }
        if (!isBlank(fund.getFundType())) {
            return fund.getFundType();
        }
        return fund.getFonTurAciklama();
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


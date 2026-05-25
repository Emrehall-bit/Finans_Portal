package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.entity.MarketPriceHistory;
import com.emrehalli.financeportal.market.domain.enums.IntervalType;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceHistoryRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed market query service.
 */
@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class JpaMarketQueryService implements MarketQueryService {

    private static final IntervalType DEFAULT_HISTORY_INTERVAL = IntervalType.ONE_DAY;

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;

    @Override
    public Optional<MarketSnapshot> findBySymbol(String symbol) {
        return findBySymbol(symbol, null);
    }

    @Override
    public Optional<MarketSnapshot> findBySymbol(String symbol, com.emrehalli.financeportal.market.domain.enums.InstrumentType instrumentType) {
        return resolveInstrument(symbol, instrumentType)
                .flatMap(this::mapLatestSnapshot);
    }

    @Override
    public List<HistoricalPrice> getHistory(String symbol, SourceName sourceName, LocalDate from, LocalDate to) {
        Optional<MarketInstrument> instrumentOptional = marketInstrumentRepository
                .findFirstByInstrumentCodeIgnoreCaseOrderByCreatedAtAsc(symbol);
        if (instrumentOptional.isEmpty()) {
            return List.of();
        }

        Instant start = from != null
                ? from.atStartOfDay().toInstant(ZoneOffset.UTC)
                : LocalDate.MIN.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to != null
                ? to.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC)
                : Instant.now();

        List<MarketPriceHistory> history = sourceName == null
                ? marketPriceHistoryRepository.findByInstrumentAndIntervalTypeAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                        instrumentOptional.get(),
                        DEFAULT_HISTORY_INTERVAL,
                        start,
                        end
                )
                : marketPriceHistoryRepository.findByInstrumentAndIntervalTypeAndSourceNameAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                        instrumentOptional.get(),
                        DEFAULT_HISTORY_INTERVAL,
                        sourceName,
                        start,
                        end
                );

        return history.stream()
                .map(price -> new HistoricalPrice(
                        price.getInstrument().getInstrumentCode(),
                        price.getPriceTimestamp().atZone(ZoneOffset.UTC).toLocalDate(),
                        price.getClosePrice()
                ))
                .toList();
    }

    private Optional<MarketInstrument> resolveInstrument(
            String symbol,
            com.emrehalli.financeportal.market.domain.enums.InstrumentType instrumentType
    ) {
        if (instrumentType != null) {
            return marketInstrumentRepository
                    .findFirstByInstrumentCodeIgnoreCaseAndInstrumentTypeOrderByCreatedAtAsc(symbol, instrumentType);
        }

        return marketInstrumentRepository.findFirstByInstrumentCodeIgnoreCaseOrderByCreatedAtAsc(symbol);
    }

    private Optional<MarketSnapshot> mapLatestSnapshot(MarketInstrument instrument) {
        return marketPriceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument)
                .map(price -> new MarketSnapshot(
                        instrument.getInstrumentCode(),
                        instrument.getInstrumentName(),
                        price.getPriceValue(),
                        price.getChangeRate(),
                        price.getSourceName().name(),
                        instrument.getInstrumentType().name(),
                        null,
                        price.getPriceTimestamp()
                ));
    }
}




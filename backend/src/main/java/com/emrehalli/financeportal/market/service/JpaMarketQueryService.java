package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.market.domain.enums.SourceName;
import com.emrehalli.financeportal.market.persistence.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.MarketPriceRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed market query service.
 */
@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class JpaMarketQueryService implements MarketQueryService {

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketPriceRepository marketPriceRepository;

    @Override
    public Optional<MarketSnapshot> findBySymbol(String symbol) {
        return marketInstrumentRepository.findByInstrumentCodeIgnoreCase(symbol)
                .flatMap(this::mapLatestSnapshot);
    }

    @Override
    public List<HistoricalPrice> getHistory(String symbol, SourceName sourceName, LocalDate from, LocalDate to) {
        Optional<MarketInstrument> instrumentOptional = marketInstrumentRepository.findByInstrumentCodeIgnoreCase(symbol);
        if (instrumentOptional.isEmpty()) {
            return List.of();
        }

        LocalDateTime start = from != null ? from.atStartOfDay() : LocalDate.MIN.atStartOfDay();
        LocalDateTime end = to != null ? to.plusDays(1).atStartOfDay().minusNanos(1) : LocalDateTime.now();

        return marketPriceRepository.findByInstrumentAndPriceTimestampBetweenOrderByPriceTimestampAsc(
                        instrumentOptional.get(),
                        start,
                        end
                ).stream()
                .filter(price -> sourceName == null || sourceName == price.getSourceName())
                .map(price -> new HistoricalPrice(
                        price.getInstrument().getInstrumentCode(),
                        price.getPriceTimestamp().toLocalDate(),
                        price.getPriceValue()
                ))
                .toList();
    }

    private Optional<MarketSnapshot> mapLatestSnapshot(MarketInstrument instrument) {
        return marketPriceRepository.findTopByInstrumentOrderByPriceTimestampDesc(instrument)
                .map(price -> new MarketSnapshot(
                        instrument.getInstrumentCode(),
                        instrument.getInstrumentName(),
                        price.getPriceValue(),
                        null,
                        price.getSourceName().name(),
                        instrument.getInstrumentType().name(),
                        null,
                        price.getPriceTimestamp()
                ));
    }
}

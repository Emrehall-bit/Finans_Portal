package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.entity.MarketDataSnapshot;
import com.emrehalli.financeportal.market.enums.MarketDataFreshness;
import com.emrehalli.financeportal.market.repository.MarketDataSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class MarketPersistenceService {

    private final MarketDataSnapshotRepository marketDataSnapshotRepository;

    public MarketPersistenceService(MarketDataSnapshotRepository marketDataSnapshotRepository) {
        this.marketDataSnapshotRepository = marketDataSnapshotRepository;
    }

    @Transactional
    public void saveSnapshots(List<MarketDataDto> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        List<MarketDataSnapshot> entities = snapshots.stream()
                .map(this::toEntity)
                .filter(Objects::nonNull)
                .toList();

        if (entities.isEmpty()) {
            return;
        }

        marketDataSnapshotRepository.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public List<MarketDataDto> getHistoricalData(String symbol, LocalDateTime start, LocalDateTime end) {
        if (symbol == null || symbol.isBlank() || start == null || end == null) {
            return List.of();
        }

        return marketDataSnapshotRepository
                .findBySymbolIgnoreCaseAndFetchedAtBetweenOrderByFetchedAtAsc(symbol, start, end)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // Exposes the most recent persisted quote for fallback-oriented consumers.
    @Transactional(readOnly = true)
    public Optional<MarketDataDto> findLatestBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }

        return marketDataSnapshotRepository.findFirstBySymbolIgnoreCaseOrderByFetchedAtDesc(symbol)
                .map(this::toDto);
    }

    private MarketDataSnapshot toEntity(MarketDataDto dto) {
        if (dto == null
                || dto.getSymbol() == null
                || dto.getName() == null
                || dto.getInstrumentType() == null
                || dto.getPrice() == null
                || dto.getSource() == null) {
            return null;
        }

        LocalDateTime fetchedAt = dto.getFetchedAt() != null ? dto.getFetchedAt() : LocalDateTime.now();

        return MarketDataSnapshot.builder()
                .symbol(dto.getSymbol())
                .name(dto.getName())
                .instrumentType(dto.getInstrumentType())
                .price(dto.getPrice())
                .changeAmount(dto.getChangeAmount())
                .changePercent(dto.getChangePercent())
                .currency(dto.getCurrency())
                .priceTime(dto.getPriceTime())
                .fetchedAt(fetchedAt)
                .source(dto.getSource())
                .build();
    }

    private MarketDataDto toDto(MarketDataSnapshot snapshot) {
        return MarketDataDto.builder()
                .symbol(snapshot.getSymbol())
                .name(snapshot.getName())
                .instrumentType(snapshot.getInstrumentType())
                .price(snapshot.getPrice())
                .changeAmount(snapshot.getChangeAmount())
                .changePercent(snapshot.getChangePercent())
                .currency(snapshot.getCurrency())
                .priceTime(snapshot.getPriceTime())
                .fetchedAt(snapshot.getFetchedAt())
                .source(snapshot.getSource())
                .freshness(MarketDataFreshness.from(snapshot.getPriceTime(), snapshot.getFetchedAt()))
                .build();
    }
}

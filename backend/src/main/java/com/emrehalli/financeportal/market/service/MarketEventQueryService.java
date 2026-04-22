package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.dto.event.MarketEventDto;
import com.emrehalli.financeportal.market.entity.MarketEvent;
import com.emrehalli.financeportal.market.repository.MarketEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MarketEventQueryService {

    private final MarketEventRepository marketEventRepository;

    public MarketEventQueryService(MarketEventRepository marketEventRepository) {
        this.marketEventRepository = marketEventRepository;
    }

    @Transactional(readOnly = true)
    public List<MarketEventDto> getLatestEvents() {
        return marketEventRepository.findTop50ByOrderByPublishedAtDesc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MarketEventDto> getEventsBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }

        return marketEventRepository.findBySymbolIgnoreCaseOrderByPublishedAtDesc(symbol)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MarketEventDto> getEventsByType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return List.of();
        }

        return marketEventRepository.findByEventTypeIgnoreCaseOrderByPublishedAtDesc(eventType)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MarketEventDto> getEventsBySource(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }

        return marketEventRepository.findBySourceIgnoreCaseOrderByPublishedAtDesc(source)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MarketEventDto> getIpoEvents() {
        return marketEventRepository.findTop50ByOrderByPublishedAtDesc()
                .stream()
                .filter(event -> event.getEventType() != null)
                .filter(event -> event.getEventType().equalsIgnoreCase("IPO"))
                .map(this::toDto)
                .toList();
    }

    private MarketEventDto toDto(MarketEvent event) {
        return MarketEventDto.builder()
                .source(event.getSource())
                .eventType(event.getEventType())
                .title(event.getTitle())
                .symbol(event.getSymbol())
                .issuerCode(event.getIssuerCode())
                .publishedAt(event.getPublishedAt())
                .url(event.getUrl())
                .summary(event.getSummary())
                .rawPayload(event.getRawPayload())
                .fetchedAt(event.getFetchedAt())
                .build();
    }
}

package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.dto.event.MarketEventDto;
import com.emrehalli.financeportal.market.entity.MarketEvent;
import com.emrehalli.financeportal.market.repository.MarketEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class MarketEventPersistenceService {

    private final MarketEventRepository marketEventRepository;

    public MarketEventPersistenceService(MarketEventRepository marketEventRepository) {
        this.marketEventRepository = marketEventRepository;
    }

    @Transactional
    public void saveEvents(List<MarketEventDto> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        List<MarketEvent> candidates = events.stream()
                .map(this::toEntity)
                .filter(Objects::nonNull)
                .toList();

        if (candidates.isEmpty()) {
            return;
        }

        List<String> candidateUrls = candidates.stream()
                .map(MarketEvent::getUrl)
                .filter(Objects::nonNull)
                .filter(url -> !url.isBlank())
                .toList();

        Set<String> existingUrls = candidateUrls.isEmpty()
                ? Set.of()
                : new HashSet<>(marketEventRepository.findByUrlIn(candidateUrls)
                        .stream()
                        .map(MarketEvent::getUrl)
                        .filter(Objects::nonNull)
                        .toList());

        List<MarketEvent> entities = candidates.stream()
                .filter(entity -> entity.getUrl() == null
                        || entity.getUrl().isBlank()
                        || !existingUrls.contains(entity.getUrl()))
                .toList();

        if (entities.isEmpty()) {
            return;
        }

        marketEventRepository.saveAll(entities);
    }

    private MarketEvent toEntity(MarketEventDto dto) {
        if (dto == null
                || dto.getSource() == null
                || dto.getEventType() == null
                || dto.getTitle() == null
                || dto.getPublishedAt() == null) {
            return null;
        }

        return MarketEvent.builder()
                .source(dto.getSource())
                .eventType(dto.getEventType())
                .title(dto.getTitle())
                .symbol(dto.getSymbol())
                .issuerCode(dto.getIssuerCode())
                .publishedAt(dto.getPublishedAt())
                .url(dto.getUrl())
                .summary(dto.getSummary())
                .rawPayload(dto.getRawPayload())
                .fetchedAt(dto.getFetchedAt() != null ? dto.getFetchedAt() : LocalDateTime.now())
                .build();
    }
}

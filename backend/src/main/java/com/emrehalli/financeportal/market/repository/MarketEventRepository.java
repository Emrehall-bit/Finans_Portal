package com.emrehalli.financeportal.market.repository;

import com.emrehalli.financeportal.market.entity.MarketEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MarketEventRepository extends JpaRepository<MarketEvent, Long> {

    List<MarketEvent> findTop50ByOrderByPublishedAtDesc();

    List<MarketEvent> findBySymbolIgnoreCaseOrderByPublishedAtDesc(String symbol);

    List<MarketEvent> findByEventTypeIgnoreCaseOrderByPublishedAtDesc(String eventType);

    List<MarketEvent> findBySourceIgnoreCaseOrderByPublishedAtDesc(String source);

    List<MarketEvent> findByUrlIn(Collection<String> urls);

    List<MarketEvent> findByPublishedAtBetween(LocalDateTime start, LocalDateTime end);
}

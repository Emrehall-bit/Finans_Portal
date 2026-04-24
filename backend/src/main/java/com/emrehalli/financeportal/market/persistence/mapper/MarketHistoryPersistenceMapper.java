package com.emrehalli.financeportal.market.persistence.mapper;

import com.emrehalli.financeportal.market.persistence.entity.MarketHistoryEntity;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MarketHistoryPersistenceMapper {

    public MarketHistoryEntity toEntity(MarketHistoryRecord record) {
        MarketHistoryEntity entity = new MarketHistoryEntity();
        entity.setSymbol(record.symbol());
        entity.setDisplayName(record.displayName());
        entity.setInstrumentType(record.instrumentType());
        entity.setSource(record.source());
        entity.setPriceDate(record.priceDate());
        entity.setClosePrice(record.closePrice());
        entity.setCurrency(record.currency());
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    public MarketHistoryRecord toRecord(MarketHistoryEntity entity) {
        return new MarketHistoryRecord(
                entity.getSymbol(),
                entity.getDisplayName(),
                entity.getInstrumentType(),
                entity.getSource(),
                entity.getPriceDate(),
                entity.getClosePrice(),
                entity.getCurrency()
        );
    }
}

package com.emrehalli.financeportal.market.api.mapper;

import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.service.model.CurrentPriceSnapshot;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import org.springframework.stereotype.Component;

@Component
public class MarketApiMapper {

    public MarketQuoteResponse toResponse(MarketQuote quote) {
        return new MarketQuoteResponse(
                quote.symbol(),
                quote.displayName(),
                quote.instrumentType().name(),
                quote.price(),
                quote.changeRate(),
                quote.currency(),
                quote.source().name(),
                quote.priceTime(),
                quote.fetchedAt(),
                "LIVE",
                quote.fetchedAt() != null ? quote.fetchedAt() : quote.priceTime()
        );
    }

    public MarketQuoteResponse toResponse(CurrentPriceSnapshot snapshot) {
        return new MarketQuoteResponse(
                snapshot.symbol(),
                snapshot.displayName(),
                snapshot.instrumentType() == null ? null : snapshot.instrumentType().name(),
                snapshot.price(),
                snapshot.changeRate(),
                snapshot.currency(),
                snapshot.source() == null ? null : snapshot.source().name(),
                snapshot.priceTime(),
                snapshot.fetchedAt(),
                snapshot.priceStatus().name(),
                snapshot.lastUpdatedAt()
        );
    }

    public com.emrehalli.financeportal.market.api.dto.MarketHistoryResponse toHistoryResponse(MarketHistoryRecord record) {
        return new com.emrehalli.financeportal.market.api.dto.MarketHistoryResponse(
                record.symbol(),
                record.priceDate(),
                record.closePrice(),
                record.source().name(),
                record.currency()
        );
    }
}

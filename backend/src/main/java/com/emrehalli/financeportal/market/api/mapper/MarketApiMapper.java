package com.emrehalli.financeportal.market.api.mapper;

import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.domain.MarketQuote;
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
                quote.fetchedAt()
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

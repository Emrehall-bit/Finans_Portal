package com.emrehalli.financeportal.market.api.mapper;

import com.emrehalli.financeportal.market.api.dto.MarketBackfillJobResponse;
import com.emrehalli.financeportal.market.api.dto.MarketHistoryBackfillResponse;
import com.emrehalli.financeportal.market.api.dto.MarketQuoteResponse;
import com.emrehalli.financeportal.market.api.dto.admin.InstrumentAdminResponse;
import com.emrehalli.financeportal.market.api.dto.admin.InstrumentMappingAdminResponse;
import com.emrehalli.financeportal.market.api.dto.admin.MarketProviderHealthResponse;
import com.emrehalli.financeportal.market.domain.MarketQuote;
import com.emrehalli.financeportal.market.persistence.entity.MarketInstrumentEntity;
import com.emrehalli.financeportal.market.persistence.entity.MarketProviderMappingEntity;
import com.emrehalli.financeportal.market.service.MarketProviderHealthService;
import com.emrehalli.financeportal.market.service.model.CurrentPriceSnapshot;
import com.emrehalli.financeportal.market.service.model.MarketHistoryRecord;
import com.emrehalli.financeportal.market.service.model.MarketBackfillJobResult;
import com.emrehalli.financeportal.market.service.model.MarketHistoryPersistenceResult;
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

    public MarketHistoryBackfillResponse toBackfillResponse(MarketHistoryPersistenceResult result, int lookbackDays) {
        return new MarketHistoryBackfillResponse(
                result.source().name(),
                lookbackDays,
                result.received(),
                result.saved(),
                result.skippedDuplicate()
        );
    }

    public MarketBackfillJobResponse toBackfillJobResponse(MarketBackfillJobResult result) {
        return new MarketBackfillJobResponse(
                result.providerSource().name(),
                result.symbol(),
                result.status().name(),
                result.fetchedCount(),
                result.savedCount(),
                result.minDate(),
                result.maxDate(),
                result.retryCount(),
                result.message()
        );
    }

    public MarketProviderHealthResponse toProviderHealthResponse(MarketProviderHealthService.ProviderHealthSnapshot snapshot) {
        return new MarketProviderHealthResponse(
                snapshot.source().name(),
                snapshot.circuitBreakerState(),
                snapshot.lastSuccessAt(),
                snapshot.lastFailureAt(),
                snapshot.failedMappingCount(),
                snapshot.totalMappingCount()
        );
    }

    public InstrumentAdminResponse toInstrumentAdminResponse(MarketInstrumentEntity entity) {
        return new InstrumentAdminResponse(
                entity.getId(),
                entity.getSymbol(),
                entity.getDisplayName(),
                entity.getType().name(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public InstrumentMappingAdminResponse toInstrumentMappingAdminResponse(MarketProviderMappingEntity entity) {
        return new InstrumentMappingAdminResponse(
                entity.getId(),
                entity.getInstrument().getId(),
                entity.getSource().name(),
                entity.getExternalSymbol(),
                entity.getPriority(),
                entity.getRefreshIntervalMinutes(),
                entity.getHistoryStartDate(),
                entity.isEnabled(),
                entity.getLastRefreshedAt(),
                entity.getLastRefreshStatus().name(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

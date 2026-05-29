package com.emrehalli.financeportal.ai.context;

import com.emrehalli.financeportal.ai.dto.AiFundamentalAnalysisResponse;
import com.emrehalli.financeportal.ai.dto.AiTechnicalAnalysisResponse;
import com.emrehalli.financeportal.ai.service.AiFundamentalAnalysisService;
import com.emrehalli.financeportal.ai.service.AiTechnicalAnalysisService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AiContextEnricher {

    private static final Logger logger = LogManager.getLogger(AiContextEnricher.class);

    private final AiTechnicalAnalysisService technicalAnalysisService;
    private final AiFundamentalAnalysisService fundamentalAnalysisService;
    private final AiContextBuilder contextBuilder;

    public AiContextEnricher(AiTechnicalAnalysisService technicalAnalysisService,
                              AiFundamentalAnalysisService fundamentalAnalysisService,
                              AiContextBuilder contextBuilder) {
        this.technicalAnalysisService = technicalAnalysisService;
        this.fundamentalAnalysisService = fundamentalAnalysisService;
        this.contextBuilder = contextBuilder;
    }

    /**
     * Enriches a raw AiContext into a prompt-ready text block.
     * Returns null if context is null, type is unknown, or enrichment yields no data.
     */
    public String buildContextBlock(AiContext context) {
        if (context == null || context.type() == null) {
            return null;
        }
        try {
            return switch (context.type()) {
                case INSTRUMENT_DETAIL, TECHNICAL_ANALYSIS, FUNDAMENTAL_ANALYSIS -> {
                    Optional<InstrumentContext> instrumentContext = enrichInstrument(context);
                    yield instrumentContext.map(contextBuilder::buildInstrumentBlock).orElse(null);
                }
                case DASHBOARD -> contextBuilder.buildDashboardBlock(
                        new DashboardContext("KullanÄ±cÄ± dashboard ekranÄ±nda; genel piyasa gÃ¶rÃ¼nÃ¼mÃ¼nÃ¼ inceliyor.")
                );
                case MARKET_OVERVIEW -> "=== EKRAN BAÄLAMI: PÄ°YASA GENEL GÃ–RÃœNÃœMÃœ ===\n" +
                        "KullanÄ±cÄ± piyasa listesini inceliyor.";
            };
        } catch (Exception e) {
            logger.warn("Context enrichment failed. type={}, reason={}", context.type(), e.getMessage());
            return null;
        }
    }

    private Optional<InstrumentContext> enrichInstrument(AiContext context) {
        String symbol = context.symbol();
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }

        AiTechnicalAnalysisResponse technical = null;
        AiFundamentalAnalysisResponse fundamental = null;

        try {
            technical = technicalAnalysisService.getTechnicalComment(symbol);
        } catch (Exception e) {
            logger.debug("Technical analysis unavailable for context. symbol={}", symbol);
        }

        if ("STOCK".equalsIgnoreCase(context.instrumentType())) {
            try {
                fundamental = fundamentalAnalysisService.getFundamentalComment(symbol);
            } catch (Exception e) {
                logger.debug("Fundamental analysis unavailable for context. symbol={}", symbol);
            }
        }

        if (technical == null && fundamental == null) {
            return Optional.empty();
        }

        return Optional.of(new InstrumentContext(symbol, context.instrumentType(), technical, fundamental));
    }
}





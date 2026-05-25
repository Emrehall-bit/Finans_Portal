package com.emrehalli.financeportal.ai.portfolio;

import com.emrehalli.financeportal.ai.portfolio.PortfolioAnalysisContext.PositionSnapshot;
import com.emrehalli.financeportal.ai.portfolio.PortfolioAnalysisResponse.DataQuality;
import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.service.AiGatewayService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.CachedValue;
import com.emrehalli.financeportal.ai.service.AiResponseCacheService.LookupResult;
import com.emrehalli.financeportal.ai.service.AiResponseLogHelper;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import com.emrehalli.financeportal.portfolio.service.PortfolioHoldingService;
import com.emrehalli.financeportal.portfolio.service.PortfolioValuationResult;
import com.emrehalli.financeportal.portfolio.service.PortfolioService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

@Service
public class PortfolioAnalysisService {

    private static final Logger logger = LogManager.getLogger(PortfolioAnalysisService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final PortfolioService portfolioService;
    private final PortfolioHoldingService portfolioHoldingService;
    private final PortfolioAnalysisPromptBuilder promptBuilder;
    private final AiGatewayService aiGatewayService;
    private final AiResponseCacheService cacheService;
    private final AiResponsePostProcessor postProcessor;
    private final ObjectMapper objectMapper;
    private final AiResponseLogHelper responseLogHelper;

    public PortfolioAnalysisService(PortfolioService portfolioService,
                                    PortfolioHoldingService portfolioHoldingService,
                                    PortfolioAnalysisPromptBuilder promptBuilder,
                                    AiGatewayService aiGatewayService,
                                    AiResponseCacheService cacheService,
                                    AiResponsePostProcessor postProcessor,
                                    ObjectMapper objectMapper,
                                    AiResponseLogHelper responseLogHelper) {
        this.portfolioService = portfolioService;
        this.portfolioHoldingService = portfolioHoldingService;
        this.promptBuilder = promptBuilder;
        this.aiGatewayService = aiGatewayService;
        this.cacheService = cacheService;
        this.postProcessor = postProcessor;
        this.objectMapper = objectMapper;
        this.responseLogHelper = responseLogHelper;
    }

    public PortfolioAnalysisResponse getPortfolioAnalysis(Long portfolioId) {
        Portfolio portfolio = portfolioService.getPortfolioEntityById(portfolioId);
        PortfolioValuationResult valuation = portfolioHoldingService.getPortfolioValuation(portfolioId);
        List<PortfolioHoldingDto> holdings = valuation.holdings();
        PortfolioSummaryResponse summary = valuation.summary();
        String cacheKey = buildCacheKey(portfolio, holdings);

        try {
            LookupResult<PortfolioAnalysisResponse> lookup = cacheService.getOrComputeWithDynamicTtlStatus(
                    cacheKey,
                    PortfolioAnalysisResponse.class,
                    () -> compute(portfolio, holdings, summary)
            );
            PortfolioAnalysisResponse response = withCacheHit(lookup.value(), lookup.cacheHit());
            responseLogHelper.log(AiTaskType.PORTFOLIO_ANALYSIS, response.metadata());
            return response;
        } catch (Exception exception) {
            logger.warn("AI portfolio endpoint critical failure. portfolioId={}, reason={}", portfolioId, exception.getMessage());
            PortfolioAnalysisResponse response = deterministicFallback(buildContext(portfolio, holdings, summary));
            responseLogHelper.log(AiTaskType.PORTFOLIO_ANALYSIS, response.metadata());
            return response;
        }
    }

    String buildCacheKey(Portfolio portfolio, List<PortfolioHoldingDto> holdings) {
        return "ai:portfolio-analysis:" + portfolio.getId() + ":" + computeHoldingsHash(portfolio, holdings);
    }

    private CachedValue<PortfolioAnalysisResponse> compute(Portfolio portfolio,
                                                           List<PortfolioHoldingDto> holdings,
                                                           PortfolioSummaryResponse summary) {
        PortfolioAnalysisContext context = buildContext(portfolio, holdings, summary);
        PortfolioAnalysisResponse fallback = deterministicFallback(context);

        if (holdings.isEmpty()) {
            return new CachedValue<>(fallback, CACHE_TTL);
        }

        String prompt = promptBuilder.build(context);
        Optional<AiResponse> aiResponse = aiGatewayService.generate(AiTaskType.PORTFOLIO_ANALYSIS, prompt);
        if (aiResponse.isEmpty()) {
            logger.warn("AI portfolio generation unavailable. portfolioId={}", portfolio.getId());
            return new CachedValue<>(fallback, CACHE_TTL);
        }

        try {
            PortfolioAnalysisResponse response = parseResponse(aiResponse.get(), fallback);
            logger.info("AI portfolio computed. portfolioId={}, provider={}, fallbackUsed={}",
                    portfolio.getId(), response.providerUsed(), response.fallbackUsed());
            return new CachedValue<>(response, CACHE_TTL);
        } catch (Exception exception) {
            logger.warn("AI portfolio parse failed. portfolioId={}, reason={}", portfolio.getId(), exception.getMessage());
            return new CachedValue<>(fallback, CACHE_TTL);
        }
    }

    private PortfolioAnalysisContext buildContext(Portfolio portfolio,
                                                  List<PortfolioHoldingDto> holdings,
                                                  PortfolioSummaryResponse summary) {
        BigDecimal totalValue = summary.getCurrentValue();
        int valuedHoldingCount = (int) holdings.stream().filter(PortfolioHoldingDto::isValuationAvailable).count();
        DataQuality dataQuality = determineDataQuality(holdings.size(), valuedHoldingCount, summary);
        List<PositionSnapshot> positions = buildPositions(holdings, totalValue);
        Map<String, BigDecimal> instrumentWeights = buildInstrumentWeights(positions);
        Map<String, BigDecimal> typeWeights = buildTypeWeights(positions);
        List<String> riskSignals = buildRiskSignals(positions, typeWeights, summary);
        List<String> suggestions = buildDeterministicSuggestions(positions, typeWeights, summary, dataQuality);

        return new PortfolioAnalysisContext(
                portfolio.getId(),
                portfolio.getPortfolioName(),
                totalValue,
                summary.getProfitLoss(),
                summary.getProfitLossPercent(),
                holdings.size(),
                valuedHoldingCount,
                positions,
                instrumentWeights,
                typeWeights,
                riskSignals,
                suggestions,
                dataQuality
        );
    }

    private List<PositionSnapshot> buildPositions(List<PortfolioHoldingDto> holdings, BigDecimal totalValue) {
        return holdings.stream()
                .map(holding -> {
                    String symbol = normalizeSymbol(holding.getInstrumentCode());
                    String instrumentType = resolveInstrumentType(symbol);
                    BigDecimal weightPercent = null;
                    if (totalValue != null && totalValue.compareTo(BigDecimal.ZERO) > 0
                            && holding.isValuationAvailable() && holding.getCurrentValue() != null) {
                        weightPercent = holding.getCurrentValue()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(totalValue, 4, RoundingMode.HALF_UP);
                    }
                    return new PositionSnapshot(
                            symbol,
                            instrumentType,
                            holding.getQuantity(),
                            holding.getBuyPrice(),
                            holding.getCurrentPrice(),
                            holding.getCurrentValue(),
                            holding.getProfitLoss(),
                            holding.getProfitLossPercent(),
                            weightPercent,
                            holding.isValuationAvailable()
                    );
                })
                .sorted(Comparator.comparing(
                        position -> position.currentValue() == null ? BigDecimal.ZERO : position.currentValue(),
                        Comparator.reverseOrder()))
                .toList();
    }

    private Map<String, BigDecimal> buildInstrumentWeights(List<PositionSnapshot> positions) {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        positions.stream()
                .filter(PositionSnapshot::valuationAvailable)
                .filter(position -> position.weightPercent() != null)
                .forEach(position -> weights.put(position.symbol(), position.weightPercent()));
        return weights;
    }

    private Map<String, BigDecimal> buildTypeWeights(List<PositionSnapshot> positions) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (PositionSnapshot position : positions) {
            if (!position.valuationAvailable() || position.currentValue() == null) {
                continue;
            }
            values.merge(position.instrumentType(), position.currentValue(), BigDecimal::add);
        }

        BigDecimal total = values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return Map.of();
        }

        Map<String, BigDecimal> percents = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(entry -> percents.put(
                        entry.getKey(),
                        entry.getValue().multiply(BigDecimal.valueOf(100)).divide(total, 4, RoundingMode.HALF_UP)
                ));
        return percents;
    }

    private List<String> buildRiskSignals(List<PositionSnapshot> positions,
                                          Map<String, BigDecimal> typeWeights,
                                          PortfolioSummaryResponse summary) {
        List<String> signals = new ArrayList<>();

        positions.stream()
                .filter(position -> position.weightPercent() != null)
                .max(Comparator.comparing(PositionSnapshot::weightPercent))
                .ifPresent(largest -> {
                    if (largest.weightPercent().compareTo(BigDecimal.valueOf(60)) > 0) {
                        signals.add(largest.symbol() + " tek basina portfoyun %60'inden fazlasini tasiyor; yogunlasma riski yuksek.");
                    } else if (largest.weightPercent().compareTo(BigDecimal.valueOf(40)) > 0) {
                        signals.add(largest.symbol() + " portfoyde baskin agirlikta; yogunlasma dikkat gerektiriyor.");
                    }
                });

        if (positions.size() <= 2 && !positions.isEmpty()) {
            signals.add("Portfoy 1-2 varlikta toplaniyor; cesitlilik dusuk.");
        }

        if (summary.getProfitLossPercent() != null && summary.getProfitLossPercent().compareTo(BigDecimal.valueOf(-15)) <= 0) {
            signals.add("Toplam zarar oranı belirgin seviyede negatif; portfoy baski altinda olabilir.");
        }

        long unavailableCount = positions.stream().filter(position -> !position.valuationAvailable()).count();
        if (unavailableCount > 0) {
            signals.add(unavailableCount + " pozisyon icin canli veya saglam fiyatlama bulunamadi.");
        }

        if (!typeWeights.containsKey("CASH_TRY")) {
            signals.add("Portfoyde TRY nakit tamponu gorunmuyor; tum risk varliklar uzerinden okunuyor olabilir.");
        }

        if (signals.isEmpty()) {
            signals.add("Belirgin bir yapisal risk sinyali cikmiyor; portfoy dagilimi genel olarak dengeli gorunuyor.");
        }
        return List.copyOf(signals);
    }

    private List<String> buildDeterministicSuggestions(List<PositionSnapshot> positions,
                                                       Map<String, BigDecimal> typeWeights,
                                                       PortfolioSummaryResponse summary,
                                                       DataQuality dataQuality) {
        List<String> suggestions = new ArrayList<>();

        if (positions.size() <= 2 && !positions.isEmpty()) {
            suggestions.add("Cesitlilik seviyesi dusuk oldugu icin portfoy yorumu birkac pozisyona asiri duyarlidir.");
        }
        if (typeWeights.size() <= 1 && !positions.isEmpty()) {
            suggestions.add("Tur bazli dagilim sinirli; portfoy tek tema etrafinda donuyor olabilir.");
        }
        if (summary.getMissingPriceCount() > 0 || dataQuality != DataQuality.COMPLETE) {
            suggestions.add("Veri kalitesi sinirli alanlarda yorum ihtiyatla okunmali.");
        }
        positions.stream()
                .filter(PositionSnapshot::valuationAvailable)
                .filter(position -> position.profitLossPercent() != null)
                .max(Comparator.comparing(PositionSnapshot::profitLossPercent))
                .ifPresent(best -> suggestions.add(best.symbol() + " portfoyde gorece en guclu performans veren pozisyon olarak one cikiyor."));
        positions.stream()
                .filter(PositionSnapshot::valuationAvailable)
                .filter(position -> position.profitLossPercent() != null)
                .min(Comparator.comparing(PositionSnapshot::profitLossPercent))
                .ifPresent(worst -> suggestions.add(worst.symbol() + " portfoyde en zayif performans veren pozisyon olarak izlenmeli."));

        if (suggestions.isEmpty()) {
            suggestions.add("Portfoy yorumu icin kullanilabilir veri sinirli ama mevcut dagilim temel bir cati sunuyor.");
        }
        return List.copyOf(suggestions);
    }

    private PortfolioAnalysisResponse deterministicFallback(PortfolioAnalysisContext context) {
        List<String> strongestPositions = context.positions().stream()
                .filter(PositionSnapshot::valuationAvailable)
                .filter(position -> position.profitLossPercent() != null)
                .sorted(Comparator.comparing(PositionSnapshot::profitLossPercent).reversed())
                .limit(3)
                .map(position -> position.symbol() + " (%" + format(position.profitLossPercent()) + ")")
                .toList();

        List<String> weakestPositions = context.positions().stream()
                .filter(PositionSnapshot::valuationAvailable)
                .filter(position -> position.profitLossPercent() != null)
                .sorted(Comparator.comparing(PositionSnapshot::profitLossPercent))
                .limit(3)
                .map(position -> position.symbol() + " (%" + format(position.profitLossPercent()) + ")")
                .toList();

        return new PortfolioAnalysisResponse(
                context.portfolioId(),
                context.portfolioName(),
                context.totalValue(),
                context.totalProfitLoss(),
                context.totalProfitLossPercent(),
                buildSummary(context),
                buildAllocationComment(context),
                buildRiskComment(context),
                buildDiversificationComment(context),
                strongestPositions,
                weakestPositions,
                context.riskSignals(),
                context.suggestions(),
                buildFinalComment(context),
                context.dataQuality(),
                null,
                false,
                AiResponseMetadata.deterministic(context.dataQuality().name())
        );
    }

    private PortfolioAnalysisResponse parseResponse(AiResponse aiResponse,
                                                    PortfolioAnalysisResponse fallback) throws Exception {
        JsonNode root = parseJson(aiResponse.content());
        String providerUsed = aiResponse.provider().name().toLowerCase(Locale.ROOT);
        return new PortfolioAnalysisResponse(
                fallback.portfolioId(),
                fallback.portfolioName(),
                fallback.totalValue(),
                fallback.totalProfitLoss(),
                fallback.totalProfitLossPercent(),
                processText(root, "summary", fallback.summary()),
                processText(root, "allocationComment", fallback.allocationComment()),
                processText(root, "riskComment", fallback.riskComment()),
                processText(root, "diversificationComment", fallback.diversificationComment()),
                processList(root, "strongestPositions", fallback.strongestPositions()),
                processList(root, "weakestPositions", fallback.weakestPositions()),
                processList(root, "riskSignals", fallback.riskSignals()),
                processList(root, "suggestions", fallback.suggestions()),
                processText(root, "finalComment", fallback.finalComment()),
                fallback.dataQuality(),
                providerUsed,
                aiResponse.fallbackUsed(),
                AiResponseMetadata.fromAiResponse(aiResponse, fallback.dataQuality().name())
        );
    }

    private JsonNode parseJson(String raw) throws Exception {
        String cleaned = raw.trim()
                .replaceAll("(?i)^\\s*```json\\s*", "")
                .replaceAll("^\\s*```\\s*", "")
                .replaceAll("\\s*```\\s*$", "")
                .trim();
        int first = cleaned.indexOf('{');
        int last = cleaned.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new IllegalArgumentException("No JSON object found in AI portfolio response");
        }
        return objectMapper.readTree(cleaned.substring(first, last + 1));
    }

    private String processText(JsonNode root, String field, String fallback) {
        String value = root.path(field).asText(null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String processed = postProcessor.process(value.trim(), AiTaskType.PORTFOLIO_ANALYSIS);
        return processed.isBlank() ? fallback : processed;
    }

    private List<String> processList(JsonNode root, String field, List<String> fallback) {
        JsonNode node = root.path(field);
        if (!node.isArray() || node.isEmpty()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String text = item.asText(null);
            if (text != null && !text.isBlank()) {
                String processed = postProcessor.process(text.trim(), AiTaskType.PORTFOLIO_ANALYSIS);
                if (!processed.isBlank()) {
                    values.add(processed);
                }
            }
        });
        return values.isEmpty() ? fallback : List.copyOf(values);
    }

    private String buildSummary(PortfolioAnalysisContext context) {
        if (context.holdingCount() == 0) {
            return "Portfoy bos oldugu icin AI yorumu temel seviye bilgiyle sinirli. Yeni pozisyonlar eklendiginde dagilim ve risk resmi daha anlamli okunur.";
        }
        return context.portfolioName() + " portfoyu " + context.holdingCount() + " pozisyondan olusuyor. Toplam deger "
                + formatMoney(context.totalValue()) + ", toplam performans ise "
                + formatMoney(context.totalProfitLoss()) + " ve %" + format(context.totalProfitLossPercent()) + " seviyesinde.";
    }

    private String buildAllocationComment(PortfolioAnalysisContext context) {
        if (context.typeWeights().isEmpty()) {
            return "Tur bazli dagilim hesaplanamadi; fiyatlanabilir portfoy verisi sinirli.";
        }
        Map.Entry<String, BigDecimal> dominant = context.typeWeights().entrySet().iterator().next();
        return "Portfoy dagiliminda " + dominant.getKey() + " grubu %" + format(dominant.getValue())
                + " ile en yuksek payi tasiyor. Enstruman bazli agirliklar yogunlasma riskini belirleyen ana unsur.";
    }

    private String buildRiskComment(PortfolioAnalysisContext context) {
        return context.riskSignals().isEmpty()
                ? "Belirgin ek risk sinyali uretilemedi."
                : context.riskSignals().getFirst();
    }

    private String buildDiversificationComment(PortfolioAnalysisContext context) {
        if (context.holdingCount() == 0) {
            return "Cesitlilik degerlendirmesi icin aktif pozisyon yok.";
        }
        if (context.holdingCount() <= 2) {
            return "Pozisyon sayisi dusuk oldugu icin cesitlilik sinirli ve tekil hareketlere duyarlilik yuksek.";
        }
        if (context.typeWeights().size() <= 1) {
            return "Portfoy farkli arac turlerine yayilmadigi icin tematik cesitlilik zayif.";
        }
        return "Pozisyon ve tur dagilimi belirli bir cesitlilik sagliyor; yine de baskin agirliklar yakindan izlenmeli.";
    }

    private String buildFinalComment(PortfolioAnalysisContext context) {
        return "Portfoy yorumu mevcut holdings dagilimi ve fiyatlanabilen pozisyonlar uzerinden uretildi. En kritik sinyal, "
                + context.riskSignals().getFirst().toLowerCase(Locale.ROOT)
                + " Veriler guncellendikce yorumun agirlik merkezi degisebilir.";
    }

    private DataQuality determineDataQuality(int holdingCount, int valuedHoldingCount, PortfolioSummaryResponse summary) {
        if (holdingCount == 0) {
            return DataQuality.LIMITED;
        }
        if (valuedHoldingCount == holdingCount && summary.getCurrentValue() != null) {
            return DataQuality.COMPLETE;
        }
        if (valuedHoldingCount > 0) {
            return DataQuality.PARTIAL;
        }
        return DataQuality.LIMITED;
    }

    private String computeHoldingsHash(Portfolio portfolio, List<PortfolioHoldingDto> holdings) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringJoiner joiner = new StringJoiner("|");
            joiner.add(String.valueOf(portfolio.getId()));
            joiner.add(nullSafe(portfolio.getPortfolioName()));
            holdings.stream()
                    .sorted(Comparator.comparing(PortfolioHoldingDto::getHoldingId))
                    .forEach(holding -> joiner
                            .add(String.valueOf(holding.getHoldingId()))
                            .add(nullSafe(holding.getInstrumentCode()))
                            .add(nullSafeDecimal(holding.getQuantity()))
                            .add(nullSafeDecimal(holding.getBuyPrice()))
                            .add(String.valueOf(holding.getUpdatedAt()))
                    );
            byte[] hash = digest.digest(joiner.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception exception) {
            logger.warn("Portfolio AI cache hash fallback used. portfolioId={}, reason={}", portfolio.getId(), exception.getMessage());
            return String.valueOf(holdings.size());
        }
    }

    private String nullSafe(String value) {
        return value == null ? "-" : value;
    }

    private String nullSafeDecimal(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private String resolveInstrumentType(String symbol) {
        if ("TRY".equalsIgnoreCase(symbol)) {
            return "CASH_TRY";
        }
        if (symbol.startsWith("TCMB:") || symbol.endsWith("TRY")) {
            return "FX";
        }
        if (symbol.endsWith("USDT") || symbol.matches("^(BTC|ETH|SOL|BNB|XRP|AVAX).*$")) {
            return "CRYPTO";
        }
        if (symbol.length() == 3) {
            return "FUND";
        }
        return "STOCK";
    }

    private String normalizeSymbol(String value) {
        return value == null ? "-" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String format(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private String formatMoney(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private PortfolioAnalysisResponse withCacheHit(PortfolioAnalysisResponse response, boolean cacheHit) {
        if (response == null) {
            return null;
        }
        AiResponseMetadata metadata = response.metadata() != null
                ? response.metadata().withCacheHit(cacheHit)
                : AiResponseMetadata.deterministic(response.dataQuality().name()).withCacheHit(cacheHit);
        return new PortfolioAnalysisResponse(
                response.portfolioId(),
                response.portfolioName(),
                response.totalValue(),
                response.totalProfitLoss(),
                response.totalProfitLossPercent(),
                response.summary(),
                response.allocationComment(),
                response.riskComment(),
                response.diversificationComment(),
                response.strongestPositions(),
                response.weakestPositions(),
                response.riskSignals(),
                response.suggestions(),
                response.finalComment(),
                response.dataQuality(),
                response.providerUsed(),
                response.fallbackUsed(),
                metadata
        );
    }
}




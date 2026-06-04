package com.emrehalli.financeportal.ai.features.comparison;

import com.emrehalli.financeportal.ai.features.comparison.ComparisonAnalysisContext.InstrumentSnapshot;
import com.emrehalli.financeportal.ai.features.comparison.ComparisonAnalysisResponse.DataQuality;
import com.emrehalli.financeportal.ai.features.fundamental.AiFundamentalAnalysisResponse.FinancialHealth;
import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ComparisonFallbackBuilder {

    ComparisonAnalysisResponse deterministicFallback(ComparisonAnalysisContext context) {
        return deterministicFallback(context.left(), context.right(), context.dataQuality());
    }

    ComparisonAnalysisResponse deterministicFallback(InstrumentSnapshot left,
                                                     InstrumentSnapshot right,
                                                     DataQuality dataQuality) {
        return new ComparisonAnalysisResponse(
                left.symbol(),
                right.symbol(),
                buildSummary(left, right, dataQuality),
                buildTechnicalComparison(left, right),
                buildFundamentalComparison(left, right),
                buildRiskComparison(left, right),
                mergeStrengths(left.technicalStrengths(), left.fundamentalStrengths()),
                mergeStrengths(right.technicalStrengths(), right.fundamentalStrengths()),
                mergeStrengths(left.technicalWeaknesses(), left.fundamentalWeaknesses()),
                mergeStrengths(right.technicalWeaknesses(), right.fundamentalWeaknesses()),
                buildFinalComment(left, right, dataQuality),
                dataQuality,
                null,
                false,
                AiResponseMetadata.deterministic(dataQuality.name())
        );
    }

    ComparisonAnalysisResponse withCacheHit(ComparisonAnalysisResponse response, boolean cacheHit) {
        if (response == null) {
            return null;
        }
        AiResponseMetadata metadata = response.metadata() != null
                ? response.metadata().withCacheHit(cacheHit)
                : AiResponseMetadata.deterministic(response.dataQuality().name()).withCacheHit(cacheHit);
        return new ComparisonAnalysisResponse(
                response.leftSymbol(),
                response.rightSymbol(),
                response.summary(),
                response.technicalComparison(),
                response.fundamentalComparison(),
                response.riskComparison(),
                response.strengthsLeft(),
                response.strengthsRight(),
                response.weaknessesLeft(),
                response.weaknessesRight(),
                response.finalComment(),
                response.dataQuality(),
                response.providerUsed(),
                response.fallbackUsed(),
                metadata
        );
    }

    private String buildSummary(InstrumentSnapshot left, InstrumentSnapshot right, DataQuality dataQuality) {
        String technicalLeader = left.technicalSignal().ordinal() <= right.technicalSignal().ordinal()
                ? left.symbol()
                : right.symbol();
        String riskier = left.overallRiskScore() >= right.overallRiskScore() ? left.symbol() : right.symbol();
        return String.format(
                Locale.ROOT,
                "%s ile %s karsilastirmasinda teknik tarafta %s gorece daha dengeli dururken, risk tarafinda %s daha fazla dikkat gerektiriyor. Veri kalitesi %s seviyesinde.",
                left.symbol(),
                right.symbol(),
                technicalLeader,
                riskier,
                dataQuality.name().toLowerCase(Locale.ROOT)
        );
    }

    private String buildTechnicalComparison(InstrumentSnapshot left, InstrumentSnapshot right) {
        return left.symbol() + " teknik olarak " + describeTechnical(left)
                + "; " + right.symbol() + " ise " + describeTechnical(right)
                + ". Kisa vadeli karsilastirmada sinyal/risk dengesi birlikte okunmali.";
    }

    private String buildFundamentalComparison(InstrumentSnapshot left, InstrumentSnapshot right) {
        if (!left.fundamentalsAvailable() && !right.fundamentalsAvailable()) {
            return "Iki enstruman icin de karsilastirilabilir temel veri yok; bu katman teknik gorunumun gerisinde kaliyor.";
        }
        if (!left.fundamentalsAvailable() || !right.fundamentalsAvailable()) {
            String missing = !left.fundamentalsAvailable() ? left.symbol() : right.symbol();
            String available = !left.fundamentalsAvailable() ? right.symbol() : left.symbol();
            return available + " tarafinda temel metrikler okunabilirken, " + missing
                    + " icin ayni derinlikte temel veri yok. Bu nedenle temel karsilastirma parcali yorumlanmali.";
        }
        return left.symbol() + " finansal saglikta " + describeFinancial(left.financialHealth())
                + " profiline yakin; " + right.symbol() + " ise " + describeFinancial(right.financialHealth())
                + " gorunum sunuyor. Marj, buyume ve borcluluk dengesi birlikte degerlendirilmeli.";
    }

    private String buildRiskComparison(InstrumentSnapshot left, InstrumentSnapshot right) {
        if (left.overallRiskScore() == right.overallRiskScore()) {
            return "Toplam risk profili iki tarafta birbirine yakin. Ayrim daha cok veri kalitesi ve momentum farklarindan geliyor.";
        }
        InstrumentSnapshot riskier = left.overallRiskScore() > right.overallRiskScore() ? left : right;
        InstrumentSnapshot steadier = riskier == left ? right : left;
        return riskier.symbol() + " tarafinda teknik/fundamental riskler daha yogun gorunuyor; "
                + steadier.symbol() + " ise su an icin nispeten daha dengeli bir risk profili sunuyor.";
    }

    private String buildFinalComment(InstrumentSnapshot left, InstrumentSnapshot right, DataQuality dataQuality) {
        String balancedSide = left.overallRiskScore() <= right.overallRiskScore() ? left.symbol() : right.symbol();
        return "Premium AI karsilastirma sonucu, " + balancedSide
                + " tarafinin mevcut verilerle daha dengeli bir profil verdigini; ancak kararin zamanlama, veri kalitesi ve sektor/enstruman baglami ile birlikte okunmasi gerektigini gosteriyor. Veri kalitesi "
                + dataQuality.name().toLowerCase(Locale.ROOT) + " seviyesinde.";
    }

    private List<String> mergeStrengths(List<String> technical, List<String> fundamental) {
        List<String> merged = new ArrayList<>();
        if (technical != null) {
            merged.addAll(technical);
        }
        if (fundamental != null) {
            merged.addAll(fundamental);
        }
        return merged.isEmpty() ? List.of("Belirgin guclu yon cikmiyor.") : List.copyOf(merged.subList(0, Math.min(4, merged.size())));
    }

    private String describeTechnical(InstrumentSnapshot snapshot) {
        return snapshot.trendLabel().toLowerCase(Locale.ROOT)
                + ", sinyal " + snapshot.technicalSignal().name().toLowerCase(Locale.ROOT)
                + ", risk " + snapshot.technicalRisk().name().toLowerCase(Locale.ROOT)
                + " bir gorunume sahip";
    }

    private String describeFinancial(FinancialHealth financialHealth) {
        return switch (financialHealth) {
            case STRONG -> "gorece guclu";
            case STABLE -> "dengeli";
            case WATCH -> "izlenmesi gereken";
            case RISKY -> "kirilgan";
        };
    }
}

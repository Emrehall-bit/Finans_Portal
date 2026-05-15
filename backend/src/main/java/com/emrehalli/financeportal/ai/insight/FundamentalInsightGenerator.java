package com.emrehalli.financeportal.ai.insight;

import com.emrehalli.financeportal.company.dto.CompanyFundamentalsResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class FundamentalInsightGenerator {

    /**
     * Generates pre-interpreted financial insights so the LLM synthesizes rather than
     * re-derives meaning from raw numbers.
     *
     * @param revenue   latest period revenue (may be null)
     * @param netProfit latest period net profit (may be null)
     */
    public List<FinancialInsight> generate(CompanyFundamentalsResponse f,
                                           BigDecimal revenue,
                                           BigDecimal netProfit) {
        List<FinancialInsight> insights = new ArrayList<>();
        addProfitabilityInsights(insights, f, netProfit);
        addGrowthInsights(insights, f);
        addLeverageInsights(insights, f);
        addValuationInsights(insights, f);
        return List.copyOf(insights);
    }

    // ── Profitability ─────────────────────────────────────────────

    private void addProfitabilityInsights(List<FinancialInsight> out,
                                          CompanyFundamentalsResponse f,
                                          BigDecimal netProfit) {
        BigDecimal roe = f.getRoe();
        if (roe != null) {
            double v = pct(roe);
            if (v < 5) {
                out.add(FinancialInsight.weakness(
                        "Özkaynak verimliliği zayıf; ROE " + fmtPct(roe) + " ile özkaynak üzerinden sınırlı getiri üretiliyor."));
            } else if (v < 15) {
                out.add(FinancialInsight.neutral(
                        "Özkaynak verimliliği orta düzeyde; ROE " + fmtPct(roe) + "."));
            } else {
                out.add(FinancialInsight.strength(
                        "Şirket özkaynaklarını verimli kullanıyor; ROE " + fmtPct(roe) + " ile güçlü seyrediyor."));
            }
        }

        BigDecimal roa = f.getRoa();
        if (roa != null) {
            double v = pct(roa);
            if (v < 2) {
                out.add(FinancialInsight.weakness(
                        "Varlıkların kâr üretme kapasitesi sınırlı; ROA " + fmtPct(roa) + " seviyesinde."));
            } else if (v >= 8) {
                out.add(FinancialInsight.strength(
                        "Varlık kârlılığı güçlü; ROA " + fmtPct(roa) + " ile verimli varlık kullanımı görülüyor."));
            }
        }

        BigDecimal netMargin = f.getNetMargin();
        if (netMargin != null) {
            double v = pct(netMargin);
            if (v < 0) {
                out.add(FinancialInsight.risk(
                        "Şirket zarar üretiyor; net marj " + fmtPct(netMargin) + " ile negatif bölgede."));
            } else if (v < 10) {
                out.add(FinancialInsight.neutral(
                        "Net kârlılık sınırlı; marj " + fmtPct(netMargin) + " ile operasyonel baskı var."));
            } else if (v > 20) {
                out.add(FinancialInsight.strength(
                        "Güçlü net kâr marjı; " + fmtPct(netMargin) + " ile operasyonel verimlilik öne çıkıyor."));
            }
        } else if (netProfit != null && netProfit.compareTo(BigDecimal.ZERO) < 0) {
            out.add(FinancialInsight.risk("Son raporda net kâr negatif; kârlılık sürdürülebilirliği izlenmeli."));
        }

        BigDecimal grossMargin = f.getGrossMargin();
        if (grossMargin != null) {
            double v = pct(grossMargin);
            if (v < 15) {
                out.add(FinancialInsight.weakness(
                        "Brüt marj baskı altında; " + fmtPct(grossMargin) + " ile maliyet yapısı kârlılığı kısıtlıyor."));
            } else if (v > 40) {
                out.add(FinancialInsight.strength(
                        "Güçlü brüt marj; " + fmtPct(grossMargin) + " ile ürün/hizmet kârlılığı yüksek."));
            }
        }
    }

    // ── Growth ────────────────────────────────────────────────────

    private void addGrowthInsights(List<FinancialInsight> out, CompanyFundamentalsResponse f) {
        BigDecimal revenueGrowth = f.getRevenueGrowth();
        if (revenueGrowth != null) {
            double v = pct(revenueGrowth);
            if (v > 20) {
                out.add(FinancialInsight.growth(
                        "Gelir büyümesi güçlü; hasılat yıllık +" + fmtPctAbs(revenueGrowth) + " artış gösteriyor."));
            } else if (v >= 5) {
                out.add(FinancialInsight.growth(
                        "Gelir büyümesi dengeli; hasılat +" + fmtPctAbs(revenueGrowth) + " ile ılımlı artış sürdürüyor."));
            } else if (v < 0) {
                out.add(FinancialInsight.weakness(
                        "Gelirlerde daralma görülüyor; hasılat yıllık " + fmtPct(revenueGrowth) + " gerilemiş."));
            } else {
                out.add(FinancialInsight.neutral(
                        "Hasılat büyümesi sınırlı; +" + fmtPctAbs(revenueGrowth) + " ile ivme zayıf."));
            }
        } else if (f.getRevenueGrowthLabel() != null) {
            out.add(FinancialInsight.growth("Hasılat: " + f.getRevenueGrowthLabel() + "."));
        }

        BigDecimal netProfitGrowth = f.getNetProfitGrowth();
        if (netProfitGrowth != null) {
            double v = pct(netProfitGrowth);
            if (v > 200 && isLossTransition(f.getNetProfitGrowthLabel())) {
                out.add(FinancialInsight.growth(
                        "Kârlılığa geçiş yaşandığı için büyüme oranı baz etkisi nedeniyle yüksek görünüyor; sürdürülebilirlik izlenmeli."));
            } else if (v > 200) {
                out.add(FinancialInsight.growth(
                        "Net kâr büyümesi olağandışı yüksek (" + fmtPct(netProfitGrowth) + "); baz etkisi veya tek seferlik kalemler etkili olabilir."));
            } else if (v > 20) {
                out.add(FinancialInsight.growth(
                        "Net kâr güçlü büyüyor; yıllık +" + fmtPctAbs(netProfitGrowth) + " artış."));
            } else if (v < 0) {
                out.add(FinancialInsight.weakness(
                        "Net kâr daralıyor; yıllık " + fmtPct(netProfitGrowth) + " gerileme."));
            }
        } else if (f.getNetProfitGrowthLabel() != null) {
            out.add(FinancialInsight.growth("Net kâr: " + f.getNetProfitGrowthLabel() + "."));
        }
    }

    private boolean isLossTransition(String label) {
        if (label == null) return false;
        String lower = label.toLowerCase(Locale.forLanguageTag("tr-TR"));
        return lower.contains("zarar") || lower.contains("kâra") || lower.contains("kara dön");
    }

    // ── Leverage ──────────────────────────────────────────────────

    private void addLeverageInsights(List<FinancialInsight> out, CompanyFundamentalsResponse f) {
        BigDecimal de = f.getDebtToEquity();
        if (de == null || de.compareTo(BigDecimal.ZERO) < 0) return;
        double v = de.doubleValue();
        if (v < 1.0) {
            out.add(FinancialInsight.strength(
                    "Borçluluk baskısı düşük; D/E " + fmtRatio(de) + "x ile bilanço dengeli."));
        } else if (v <= 2.0) {
            out.add(FinancialInsight.neutral(
                    "Borçluluk yönetilebilir seviyede; D/E " + fmtRatio(de) + "x."));
        } else {
            out.add(FinancialInsight.risk(
                    "Yüksek finansal kaldıraç; D/E " + fmtRatio(de) + "x ile faiz artışı döneminde borç servisi baskısı oluşabilir."));
        }
    }

    // ── Valuation ─────────────────────────────────────────────────

    private void addValuationInsights(List<FinancialInsight> out, CompanyFundamentalsResponse f) {
        BigDecimal pe = f.getPeRatio();
        if (pe != null && pe.compareTo(BigDecimal.ZERO) > 0) {
            double v = pe.doubleValue();
            if (v < 5) {
                out.add(FinancialInsight.valuation(
                        "Düşük değerleme çarpanı; F/K " + fmtRatio(pe) + "x — hisse iskontolu görünüyor (kârlılık kalitesi incelenmeli)."));
            } else if (v <= 15) {
                out.add(FinancialInsight.valuation(
                        "Makul değerleme; F/K " + fmtRatio(pe) + "x mevcut kârlılığa göre dengeli."));
            } else if (v > 25) {
                out.add(FinancialInsight.valuation(
                        "Yüksek değerleme çarpanı; F/K " + fmtRatio(pe) + "x ile büyüme beklentileri fiyata yansımış görünüyor."));
            }
        }

        BigDecimal pb = f.getPbRatio();
        if (pb != null && pb.compareTo(BigDecimal.ZERO) > 0) {
            double v = pb.doubleValue();
            if (v < 1.0) {
                out.add(FinancialInsight.valuation(
                        "Defter değerine göre iskontolu görünüm; PD/DD " + fmtRatio(pb) + "x."));
            } else if (v > 3.0) {
                out.add(FinancialInsight.valuation(
                        "Defter değerine göre primli fiyatlama; PD/DD " + fmtRatio(pb) + "x."));
            }
        }
    }

    // ── Formatting helpers ────────────────────────────────────────

    private double pct(BigDecimal decimal) {
        return decimal.multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    private String fmtPct(BigDecimal decimal) {
        return fmtNum(pct(decimal)) + "%";
    }

    private String fmtPctAbs(BigDecimal decimal) {
        return fmtNum(Math.abs(pct(decimal))) + "%";
    }

    private String fmtRatio(BigDecimal decimal) {
        return fmtNum(decimal.doubleValue());
    }

    private String fmtNum(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value) && Math.abs(value) < 1e12) {
            return String.valueOf((long) value);
        }
        String s = String.format("%.1f", value);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}

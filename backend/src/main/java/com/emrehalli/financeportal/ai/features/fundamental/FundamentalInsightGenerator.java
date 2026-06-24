package com.emrehalli.financeportal.ai.features.fundamental;

import com.emrehalli.financeportal.company.dto.response.CompanyFundamentalsResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class FundamentalInsightGenerator {

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

    // â”€â”€ Profitability â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void addProfitabilityInsights(List<FinancialInsight> out,
                                          CompanyFundamentalsResponse f,
                                          BigDecimal netProfit) {
        BigDecimal roe = f.getRoe();
        if (roe != null) {
            double v = pct(roe);
            if (v < 5) {
                out.add(FinancialInsight.weakness(
                        "Ã–zkaynak verimliliÄŸi zayÄ±f; ROE " + fmtPct(roe) + " ile Ã¶zkaynak Ã¼zerinden sÄ±nÄ±rlÄ± getiri Ã¼retiliyor."));
            } else if (v < 15) {
                out.add(FinancialInsight.neutral(
                        "Ã–zkaynak verimliliÄŸi orta dÃ¼zeyde; ROE " + fmtPct(roe) + "."));
            } else {
                out.add(FinancialInsight.strength(
                        "Åirket Ã¶zkaynaklarÄ±nÄ± verimli kullanÄ±yor; ROE " + fmtPct(roe) + " ile gÃ¼Ã§lÃ¼ seyrediyor."));
            }
        }

        BigDecimal roa = f.getRoa();
        if (roa != null) {
            double v = pct(roa);
            if (v < 2) {
                out.add(FinancialInsight.weakness(
                        "VarlÄ±klarÄ±n kÃ¢r Ã¼retme kapasitesi sÄ±nÄ±rlÄ±; ROA " + fmtPct(roa) + " seviyesinde."));
            } else if (v >= 8) {
                out.add(FinancialInsight.strength(
                        "VarlÄ±k kÃ¢rlÄ±lÄ±ÄŸÄ± gÃ¼Ã§lÃ¼; ROA " + fmtPct(roa) + " ile verimli varlÄ±k kullanÄ±mÄ± gÃ¶rÃ¼lÃ¼yor."));
            }
        }

        BigDecimal netMargin = f.getNetMargin();
        if (netMargin != null) {
            double v = pct(netMargin);
            if (v < 0) {
                out.add(FinancialInsight.risk(
                        "Åirket zarar Ã¼retiyor; net marj " + fmtPct(netMargin) + " ile negatif bÃ¶lgede."));
            } else if (v < 10) {
                out.add(FinancialInsight.neutral(
                        "Net kÃ¢rlÄ±lÄ±k sÄ±nÄ±rlÄ±; marj " + fmtPct(netMargin) + " ile operasyonel baskÄ± var."));
            } else if (v > 20) {
                out.add(FinancialInsight.strength(
                        "GÃ¼Ã§lÃ¼ net kÃ¢r marjÄ±; " + fmtPct(netMargin) + " ile operasyonel verimlilik Ã¶ne Ã§Ä±kÄ±yor."));
            }
        } else if (netProfit != null && netProfit.compareTo(BigDecimal.ZERO) < 0) {
            out.add(FinancialInsight.risk("Son raporda net kÃ¢r negatif; kÃ¢rlÄ±lÄ±k sÃ¼rdÃ¼rÃ¼lebilirliÄŸi izlenmeli."));
        }

        BigDecimal grossMargin = f.getGrossMargin();
        if (grossMargin != null) {
            double v = pct(grossMargin);
            if (v < 15) {
                out.add(FinancialInsight.weakness(
                        "BrÃ¼t marj baskÄ± altÄ±nda; " + fmtPct(grossMargin) + " ile maliyet yapÄ±sÄ± kÃ¢rlÄ±lÄ±ÄŸÄ± kÄ±sÄ±tlÄ±yor."));
            } else if (v > 40) {
                out.add(FinancialInsight.strength(
                        "GÃ¼Ã§lÃ¼ brÃ¼t marj; " + fmtPct(grossMargin) + " ile Ã¼rÃ¼n/hizmet kÃ¢rlÄ±lÄ±ÄŸÄ± yÃ¼ksek."));
            }
        }
    }

    // â”€â”€ Growth â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void addGrowthInsights(List<FinancialInsight> out, CompanyFundamentalsResponse f) {
        BigDecimal revenueGrowth = f.getRevenueGrowth();
        if (revenueGrowth != null) {
            double v = pct(revenueGrowth);
            if (v > 20) {
                out.add(FinancialInsight.growth(
                        "Gelir bÃ¼yÃ¼mesi gÃ¼Ã§lÃ¼; hasÄ±lat yÄ±llÄ±k +" + fmtPctAbs(revenueGrowth) + " artÄ±ÅŸ gÃ¶steriyor."));
            } else if (v >= 5) {
                out.add(FinancialInsight.growth(
                        "Gelir bÃ¼yÃ¼mesi dengeli; hasÄ±lat +" + fmtPctAbs(revenueGrowth) + " ile Ä±lÄ±mlÄ± artÄ±ÅŸ sÃ¼rdÃ¼rÃ¼yor."));
            } else if (v < 0) {
                out.add(FinancialInsight.weakness(
                        "Gelirlerde daralma gÃ¶rÃ¼lÃ¼yor; hasÄ±lat yÄ±llÄ±k " + fmtPct(revenueGrowth) + " gerilemiÅŸ."));
            } else {
                out.add(FinancialInsight.neutral(
                        "HasÄ±lat bÃ¼yÃ¼mesi sÄ±nÄ±rlÄ±; +" + fmtPctAbs(revenueGrowth) + " ile ivme zayÄ±f."));
            }
        } else if (f.getRevenueGrowthLabel() != null) {
            out.add(FinancialInsight.growth("HasÄ±lat: " + f.getRevenueGrowthLabel() + "."));
        }

        BigDecimal netProfitGrowth = f.getNetProfitGrowth();
        if (netProfitGrowth != null) {
            double v = pct(netProfitGrowth);
            if (v > 200 && isLossTransition(f.getNetProfitGrowthLabel())) {
                out.add(FinancialInsight.growth(
                        "KÃ¢rlÄ±lÄ±ÄŸa geÃ§iÅŸ yaÅŸandÄ±ÄŸÄ± iÃ§in bÃ¼yÃ¼me oranÄ± baz etkisi nedeniyle yÃ¼ksek gÃ¶rÃ¼nÃ¼yor; sÃ¼rdÃ¼rÃ¼lebilirlik izlenmeli."));
            } else if (v > 200) {
                out.add(FinancialInsight.growth(
                        "Net kÃ¢r bÃ¼yÃ¼mesi olaÄŸandÄ±ÅŸÄ± yÃ¼ksek (" + fmtPct(netProfitGrowth) + "); baz etkisi veya tek seferlik kalemler etkili olabilir."));
            } else if (v > 20) {
                out.add(FinancialInsight.growth(
                        "Net kÃ¢r gÃ¼Ã§lÃ¼ bÃ¼yÃ¼yor; yÄ±llÄ±k +" + fmtPctAbs(netProfitGrowth) + " artÄ±ÅŸ."));
            } else if (v < 0) {
                out.add(FinancialInsight.weakness(
                        "Net kÃ¢r daralÄ±yor; yÄ±llÄ±k " + fmtPct(netProfitGrowth) + " gerileme."));
            }
        } else if (f.getNetProfitGrowthLabel() != null) {
            out.add(FinancialInsight.growth("Net kÃ¢r: " + f.getNetProfitGrowthLabel() + "."));
        }
    }

    private boolean isLossTransition(String label) {
        if (label == null) return false;
        String lower = label.toLowerCase(Locale.forLanguageTag("tr-TR"));
        return lower.contains("zarar") || lower.contains("kÃ¢ra") || lower.contains("kara dÃ¶n");
    }

    // â”€â”€ Leverage â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void addLeverageInsights(List<FinancialInsight> out, CompanyFundamentalsResponse f) {
        BigDecimal de = f.getDebtToEquity();
        if (de == null || de.compareTo(BigDecimal.ZERO) < 0) return;
        double v = de.doubleValue();
        if (v < 1.0) {
            out.add(FinancialInsight.strength(
                    "BorÃ§luluk baskÄ±sÄ± dÃ¼ÅŸÃ¼k; D/E " + fmtRatio(de) + "x ile bilanÃ§o dengeli."));
        } else if (v <= 2.0) {
            out.add(FinancialInsight.neutral(
                    "BorÃ§luluk yÃ¶netilebilir seviyede; D/E " + fmtRatio(de) + "x."));
        } else {
            out.add(FinancialInsight.risk(
                    "YÃ¼ksek finansal kaldÄ±raÃ§; D/E " + fmtRatio(de) + "x ile faiz artÄ±ÅŸÄ± dÃ¶neminde borÃ§ servisi baskÄ±sÄ± oluÅŸabilir."));
        }
    }

    // â”€â”€ Valuation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void addValuationInsights(List<FinancialInsight> out, CompanyFundamentalsResponse f) {
        BigDecimal pe = f.getPeRatio();
        if (pe != null && pe.compareTo(BigDecimal.ZERO) > 0) {
            double v = pe.doubleValue();
            if (v < 5) {
                out.add(FinancialInsight.valuation(
                        "DÃ¼ÅŸÃ¼k deÄŸerleme Ã§arpanÄ±; F/K " + fmtRatio(pe) + "x â€” hisse iskontolu gÃ¶rÃ¼nÃ¼yor (kÃ¢rlÄ±lÄ±k kalitesi incelenmeli)."));
            } else if (v <= 15) {
                out.add(FinancialInsight.valuation(
                        "Makul deÄŸerleme; F/K " + fmtRatio(pe) + "x mevcut kÃ¢rlÄ±lÄ±ÄŸa gÃ¶re dengeli."));
            } else if (v > 25) {
                out.add(FinancialInsight.valuation(
                        "YÃ¼ksek deÄŸerleme Ã§arpanÄ±; F/K " + fmtRatio(pe) + "x ile bÃ¼yÃ¼me beklentileri fiyata yansÄ±mÄ±ÅŸ gÃ¶rÃ¼nÃ¼yor."));
            }
        }

        BigDecimal pb = f.getPbRatio();
        if (pb != null && pb.compareTo(BigDecimal.ZERO) > 0) {
            double v = pb.doubleValue();
            if (v < 1.0) {
                out.add(FinancialInsight.valuation(
                        "Defter deÄŸerine gÃ¶re iskontolu gÃ¶rÃ¼nÃ¼m; PD/DD " + fmtRatio(pb) + "x."));
            } else if (v > 3.0) {
                out.add(FinancialInsight.valuation(
                        "Defter deÄŸerine gÃ¶re primli fiyatlama; PD/DD " + fmtRatio(pb) + "x."));
            }
        }
    }

    // â”€â”€ Formatting helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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


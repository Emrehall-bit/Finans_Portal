package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.api.dto.MarketScreenItemResponse;
import com.emrehalli.financeportal.market.provider.fund.dto.FundNavDto;
import io.micrometer.observation.annotation.Observed;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketScreeningService {

    private final EntityManager entityManager;
    private final FundService fundService;

    @Observed(name = "market.screen", contextualName = "MarketService.screenMarkets")
    public Page<MarketScreenItemResponse> screen(MarketScreenCriteria criteria) {
        int page = Math.max(criteria.getPage(), 0);
        int size = Math.min(Math.max(criteria.getSize(), 1), 500);
        boolean advancedFiltersUsed = hasAdvancedFilters(criteria);

        StringBuilder from = new StringBuilder("""
                from market_instruments mi
                join lateral (
                    select mp.price_value,
                           mp.change_rate,
                           mp.price_timestamp,
                           mp.source_name
                    from market_prices mp
                    where mp.instrument_id = mi.id
                    order by mp.price_timestamp desc, mp.id desc
                    limit 1
                ) lp on true
                left join lateral (
                    select mph.volume
                    from market_price_history mph
                    where mph.instrument_id = mi.id
                      and mph.interval_type = 'ONE_DAY'
                    order by mph.price_timestamp desc, mph.id desc
                    limit 1
                ) lh on true
                left join lateral (
                    select mph.close_price,
                           mph.price_timestamp
                    from market_price_history mph
                    where mph.instrument_id = mi.id
                      and mph.interval_type = 'ONE_DAY'
                      and mph.close_price is not null
                    order by case when mph.source_name = lp.source_name then 0 else 1 end,
                             mph.price_timestamp desc,
                             mph.id desc
                    limit 1
                ) ch on true
                left join lateral (
                    select mph.close_price
                    from market_price_history mph
                    where mph.instrument_id = mi.id
                      and mph.interval_type = 'ONE_DAY'
                      and mph.close_price is not null
                      and mph.price_timestamp < date_trunc('day', lp.price_timestamp)
                    order by case when mph.source_name = lp.source_name then 0 else 1 end,
                             mph.price_timestamp desc,
                             mph.id desc
                    limit 1
                ) pc on true
                left join lateral (
                    select mph.close_price
                    from market_price_history mph
                    where mph.instrument_id = mi.id
                      and mph.interval_type = 'ONE_DAY'
                      and mph.close_price is not null
                      and ch.price_timestamp is not null
                      and mph.price_timestamp < ch.price_timestamp
                    order by case when mph.source_name = lp.source_name then 0 else 1 end,
                             mph.price_timestamp desc,
                             mph.id desc
                    limit 1
                ) lpc on true
                left join company_profiles cp
                    on mi.instrument_type = 'STOCK'
                   and upper(cp.ticker_code) = upper(mi.instrument_code)
                left join lateral (
                    select cr.id,
                           cr.market_cap,
                           cr.pe_ratio,
                           cr.pb_ratio,
                           cr.roe,
                           cr.roa,
                           cr.net_margin,
                           cr.debt_to_equity,
                           cr.revenue_growth,
                           cr.net_profit_growth,
                           cr.calculated_at
                    from company_ratios cr
                    where cr.company_id = cp.id
                    order by cr.calculated_at desc nulls last, cr.id desc
                    limit 1
                ) lr on true
                where 1 = 1
                """);

        Map<String, Object> params = new HashMap<>();
        appendFilters(from, params, criteria, advancedFiltersUsed);

        String select = """
                select mi.instrument_code as symbol,
                       mi.instrument_name as name,
                       mi.instrument_type as type,
                       coalesce(lp.source_name::text, mi.source_name::text) as source,
                       lh.volume as volume,
                       lp.price_value as last_price,
                       %s as change_percent,
                       cp.sector as sector,
                       cp.market as market,
                       lr.market_cap as market_cap,
                       lr.pe_ratio as pe_ratio,
                       lr.pb_ratio as pb_ratio,
                       lr.roe as roe,
                       lr.roa as roa,
                       lr.net_margin as net_margin,
                       lr.debt_to_equity as debt_to_equity,
                       lr.revenue_growth as revenue_growth,
                       lr.net_profit_growth as net_profit_growth,
                       lr.calculated_at as calculated_at,
                       lp.price_timestamp as data_timestamp,
                       mi.bist_tier as bist_tier,
                       mi.stock_sector as stock_sector
                """.formatted(changePercentExpression());

        String orderBy = resolveOrderBy(criteria, advancedFiltersUsed);

        Query dataQuery = entityManager.createNativeQuery(select + from + orderBy);
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult(page * size);
        dataQuery.setMaxResults(size);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();
        List<MarketScreenItemResponse> content = rows.stream()
                .map(this::mapRow)
                .toList();
        content = enrichFundRows(content, criteria);

        Query countQuery = entityManager.createNativeQuery("select count(*) " + from);
        params.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    private void appendFilters(StringBuilder sql,
                               Map<String, Object> params,
                               MarketScreenCriteria criteria,
                               boolean advancedFiltersUsed) {
        if (hasText(criteria.getType())) {
            sql.append(" and upper(mi.instrument_type::text) = :type");
            params.put("type", criteria.getType().trim().toUpperCase(Locale.ROOT));
        }

        if (hasText(criteria.getSource())) {
            sql.append(" and upper(coalesce(lp.source_name::text, mi.source_name::text)) = :source");
            params.put("source", criteria.getSource().trim().toUpperCase(Locale.ROOT));
        }

        if (hasText(criteria.getQ())) {
            sql.append(" and (upper(mi.instrument_code) like :q or upper(mi.instrument_name) like :q)");
            params.put("q", "%" + criteria.getQ().trim().toUpperCase(Locale.ROOT) + "%");
        }

        if (criteria.getSymbols() != null && !criteria.getSymbols().isEmpty()) {
            sql.append(" and upper(mi.instrument_code) in (:symbols)");
            params.put("symbols", criteria.getSymbols().stream()
                    .filter(this::hasText)
                    .map(item -> item.trim().toUpperCase(Locale.ROOT))
                    .toList());
        }

        if (criteria.getMinPrice() != null) {
            sql.append(" and lp.price_value >= :minPrice");
            params.put("minPrice", criteria.getMinPrice());
        }
        if (criteria.getMaxPrice() != null) {
            sql.append(" and lp.price_value <= :maxPrice");
            params.put("maxPrice", criteria.getMaxPrice());
        }
        if (criteria.getMinChangePercent() != null) {
            sql.append(" and ").append(changePercentExpression()).append(" >= :minChangePercent");
            params.put("minChangePercent", criteria.getMinChangePercent());
        }
        if (criteria.getMaxChangePercent() != null) {
            sql.append(" and ").append(changePercentExpression()).append(" <= :maxChangePercent");
            params.put("maxChangePercent", criteria.getMaxChangePercent());
        }

        if (advancedFiltersUsed) {
            sql.append(" and mi.instrument_type = 'STOCK'");
        }

        if (hasText(criteria.getSector())) {
            sql.append(" and upper(cp.sector) = :sector");
            params.put("sector", criteria.getSector().trim().toUpperCase(Locale.ROOT));
        }
        if (hasText(criteria.getMarket())) {
            sql.append(" and upper(cp.market) = :market");
            params.put("market", criteria.getMarket().trim().toUpperCase(Locale.ROOT));
        }

        if (advancedFiltersUsed || Boolean.TRUE.equals(criteria.getOnlyWithFundamentals())) {
            sql.append(" and lr.id is not null");
        }

        if (criteria.getMinPe() != null) {
            sql.append(" and lr.pe_ratio is not null and lr.pe_ratio > 0 and lr.pe_ratio >= :minPe");
            params.put("minPe", criteria.getMinPe());
        }
        if (criteria.getMaxPe() != null) {
            sql.append(" and lr.pe_ratio is not null and lr.pe_ratio > 0 and lr.pe_ratio <= :maxPe");
            params.put("maxPe", criteria.getMaxPe());
        }
        if (criteria.getMinPb() != null) {
            sql.append(" and lr.pb_ratio is not null and lr.pb_ratio >= :minPb");
            params.put("minPb", criteria.getMinPb());
        }
        if (criteria.getMaxPb() != null) {
            sql.append(" and lr.pb_ratio is not null and lr.pb_ratio <= :maxPb");
            params.put("maxPb", criteria.getMaxPb());
        }
        if (criteria.getMinRoe() != null) {
            sql.append(" and lr.roe is not null and lr.roe >= :minRoe");
            params.put("minRoe", criteria.getMinRoe());
        }
        if (criteria.getMinRoa() != null) {
            sql.append(" and lr.roa is not null and lr.roa >= :minRoa");
            params.put("minRoa", criteria.getMinRoa());
        }
        if (criteria.getMaxDebtToEquity() != null) {
            sql.append(" and lr.debt_to_equity is not null and lr.debt_to_equity <= :maxDebtToEquity");
            params.put("maxDebtToEquity", criteria.getMaxDebtToEquity());
        }
        if (criteria.getMinRevenueGrowth() != null) {
            sql.append(" and lr.revenue_growth is not null and lr.revenue_growth >= :minRevenueGrowth");
            params.put("minRevenueGrowth", criteria.getMinRevenueGrowth());
        }
        if (criteria.getMinNetProfitGrowth() != null) {
            sql.append(" and lr.net_profit_growth is not null and lr.net_profit_growth >= :minNetProfitGrowth");
            params.put("minNetProfitGrowth", criteria.getMinNetProfitGrowth());
        }

        if (hasText(criteria.getBistTier())) {
            String tier = criteria.getBistTier().trim().toUpperCase(Locale.ROOT);
            switch (tier) {
                case "BIST30"  -> sql.append(" and mi.instrument_type = 'STOCK' and mi.bist_tier = 'BIST30'");
                case "BIST50"  -> sql.append(" and mi.instrument_type = 'STOCK' and mi.bist_tier in ('BIST30', 'BIST50')");
                case "BIST100" -> sql.append(" and mi.instrument_type = 'STOCK' and mi.bist_tier in ('BIST30', 'BIST50', 'BIST100')");
                default -> {}
            }
        }

        if (hasText(criteria.getStockSector())) {
            String sector = criteria.getStockSector().trim().toUpperCase(Locale.ROOT);
            if (!sector.equals("OTHER")) {
                sql.append(" and mi.instrument_type = 'STOCK' and mi.stock_sector = :stockSector");
                params.put("stockSector", sector);
            }
        }
    }

    private boolean hasAdvancedFilters(MarketScreenCriteria criteria) {
        return hasText(criteria.getSector())
                || hasText(criteria.getMarket())
                || criteria.getMinPe() != null
                || criteria.getMaxPe() != null
                || criteria.getMinPb() != null
                || criteria.getMaxPb() != null
                || criteria.getMinRoe() != null
                || criteria.getMinRoa() != null
                || criteria.getMaxDebtToEquity() != null
                || criteria.getMinRevenueGrowth() != null
                || criteria.getMinNetProfitGrowth() != null;
    }

    private String resolveOrderBy(MarketScreenCriteria criteria, boolean advancedFiltersUsed) {
        String sort = criteria.getSort();
        String normalized = hasText(sort) ? sort.trim().toLowerCase(Locale.ROOT) : "";
        boolean stockType = advancedFiltersUsed || "STOCK".equalsIgnoreCase(criteria.getType());
        if (!stockType && isStockOnlySort(normalized)) {
            return " order by mi.instrument_code asc";
        }
        return switch (normalized) {
            case "price" -> " order by lp.price_value desc nulls last, mi.instrument_code asc";
            case "price_asc" -> " order by lp.price_value asc nulls last, mi.instrument_code asc";
            case "price_desc" -> " order by lp.price_value desc nulls last, mi.instrument_code asc";
            case "changepercent", "change" -> " order by " + changePercentExpression() + " desc nulls last, mi.instrument_code asc";
            case "change_asc" -> " order by " + changePercentExpression() + " asc nulls last, mi.instrument_code asc";
            case "change_desc" -> " order by " + changePercentExpression() + " desc nulls last, mi.instrument_code asc";
            case "pe_ratio", "peratio", "pe" -> positivePeOrderBy(true);
            case "pe_asc" -> positivePeOrderBy(true);
            case "pe_desc" -> positivePeOrderBy(false);
            case "pb_ratio", "pbratio", "pb" -> " order by lr.pb_ratio asc nulls last, mi.instrument_code asc";
            case "pb_asc" -> " order by lr.pb_ratio asc nulls last, mi.instrument_code asc";
            case "pb_desc" -> " order by lr.pb_ratio desc nulls last, mi.instrument_code asc";
            case "roe" -> " order by lr.roe desc nulls last, mi.instrument_code asc";
            case "roe_desc" -> " order by lr.roe desc nulls last, mi.instrument_code asc";
            case "roa" -> " order by lr.roa desc nulls last, mi.instrument_code asc";
            case "roa_desc" -> " order by lr.roa desc nulls last, mi.instrument_code asc";
            case "net_margin", "netmargin" -> " order by lr.net_margin desc nulls last, mi.instrument_code asc";
            case "debt_to_equity", "debttoequity" -> " order by lr.debt_to_equity asc nulls last, mi.instrument_code asc";
            case "revenue_growth", "revenuegrowth" -> " order by lr.revenue_growth desc nulls last, mi.instrument_code asc";
            case "revenue_growth_desc" -> " order by lr.revenue_growth desc nulls last, mi.instrument_code asc";
            case "net_profit_growth", "netprofitgrowth" -> " order by lr.net_profit_growth desc nulls last, mi.instrument_code asc";
            case "net_profit_growth_desc" -> " order by lr.net_profit_growth desc nulls last, mi.instrument_code asc";
            case "volume" -> " order by lh.volume desc nulls last, mi.instrument_code asc";
            case "volume_asc" -> " order by lh.volume asc nulls last, mi.instrument_code asc";
            case "volume_desc" -> " order by lh.volume desc nulls last, mi.instrument_code asc";
            case "market_cap", "marketcap" -> " order by lr.market_cap desc nulls last, mi.instrument_code asc";
            case "market_cap_asc", "marketcap_asc" -> " order by lr.market_cap asc nulls last, mi.instrument_code asc";
            case "market_cap_desc", "marketcap_desc" -> " order by lr.market_cap desc nulls last, mi.instrument_code asc";
            case "calculated_at", "calculatedat" -> " order by lr.calculated_at desc nulls last, mi.instrument_code asc";
            case "calculated_at_asc", "calculatedat_asc" -> " order by lr.calculated_at asc nulls last, mi.instrument_code asc";
            case "calculated_at_desc", "calculatedat_desc" -> " order by lr.calculated_at desc nulls last, mi.instrument_code asc";
            default -> advancedFiltersUsed
                    ? " order by mi.instrument_code asc"
                    : " order by mi.instrument_type asc, mi.instrument_code asc";
        };
    }

    private boolean isStockOnlySort(String normalizedSort) {
        return switch (normalizedSort) {
            case "pe_ratio", "peratio", "pe", "pe_asc", "pe_desc",
                    "pb_ratio", "pbratio", "pb", "pb_asc", "pb_desc",
                    "roe", "roe_desc",
                    "roa", "roa_desc",
                    "net_margin", "netmargin",
                    "debt_to_equity", "debttoequity",
                    "revenue_growth", "revenuegrowth", "revenue_growth_desc",
                    "net_profit_growth", "netprofitgrowth", "net_profit_growth_desc",
                    "market_cap", "marketcap", "market_cap_asc", "marketcap_asc", "market_cap_desc", "marketcap_desc",
                    "calculated_at", "calculatedat", "calculated_at_asc", "calculatedat_asc", "calculated_at_desc", "calculatedat_desc" -> true;
            default -> false;
        };
    }

    private String changePercentExpression() {
        return """
                coalesce(
                    case
                        when ch.close_price is not null
                             and ch.price_timestamp is not null
                             and ch.price_timestamp < date_trunc('day', lp.price_timestamp)
                             and round(lp.price_value, 4) = round(ch.close_price, 4)
                             and lpc.close_price is not null
                             and lpc.close_price <> 0
                        then round(((ch.close_price - lpc.close_price) / abs(lpc.close_price)) * 100, 4)
                        when pc.close_price is not null
                             and pc.close_price <> 0
                        then round(((lp.price_value - pc.close_price) / abs(pc.close_price)) * 100, 4)
                        else null
                    end,
                    lp.change_rate
                )
                """;
    }

    private String positivePeOrderBy(boolean ascending) {
        String direction = ascending ? "asc" : "desc";
        return " order by"
                + " case when lr.pe_ratio is not null and lr.pe_ratio > 0 then 0"
                + " when lr.pe_ratio is not null then 1 else 2 end,"
                + " case when lr.pe_ratio > 0 then lr.pe_ratio end "
                + direction + " nulls last, mi.instrument_code asc";
    }

    private MarketScreenItemResponse mapRow(Object[] row) {
        String type = asString(row[2]);
        return MarketScreenItemResponse.builder()
                .symbol(asString(row[0]))
                .name(asString(row[1]))
                .type(type)
                .displayUnit(resolveDisplayUnit(type))
                .currency(resolveDisplayCurrency(type))
                .source(asString(row[3]))
                .volume(asBigDecimal(row[4]))
                .lastPrice(asBigDecimal(row[5]))
                .changePercent(asBigDecimal(row[6]))
                .sector(asString(row[7]))
                .market(asString(row[8]))
                .marketCap(asBigDecimal(row[9]))
                .peRatio(asBigDecimal(row[10]))
                .pbRatio(asBigDecimal(row[11]))
                .roe(asBigDecimal(row[12]))
                .roa(asBigDecimal(row[13]))
                .netMargin(asBigDecimal(row[14]))
                .debtToEquity(asBigDecimal(row[15]))
                .revenueGrowth(asBigDecimal(row[16]))
                .netProfitGrowth(asBigDecimal(row[17]))
                .calculatedAt(asOffsetDateTime(row[18]))
                .dataTimestamp(asLocalDateTime(row[19]))
                .bistTier(asString(row[20]))
                .stockSector(asString(row[21]))
                .fundType(decodeFundType(asString(row[1])))
                .build();
    }

    private String resolveDisplayUnit(String type) {
        return "INDEX".equalsIgnoreCase(type) ? "POINT" : "CURRENCY";
    }

    private String resolveDisplayCurrency(String type) {
        return "INDEX".equalsIgnoreCase(type) ? null : "TRY";
    }

    private List<MarketScreenItemResponse> enrichFundRows(List<MarketScreenItemResponse> rows, MarketScreenCriteria criteria) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }

        boolean fundScreen = "FUND".equalsIgnoreCase(criteria.getType());
        boolean hasFundRows = fundScreen || rows.stream().anyMatch(row -> "FUND".equalsIgnoreCase(row.getType()));
        if (!hasFundRows) {
            return rows;
        }

        Map<String, FundNavDto> fundsByCode = fundService.getAll().stream()
                .filter(fund -> hasText(fund.getFundCode()))
                .collect(Collectors.toMap(
                        fund -> fund.getFundCode().trim().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left
                ));

        return rows.stream()
                .map(row -> enrichFundRow(row, fundsByCode))
                .toList();
    }

    private MarketScreenItemResponse enrichFundRow(MarketScreenItemResponse row, Map<String, FundNavDto> fundsByCode) {
        if (row == null || !"FUND".equalsIgnoreCase(row.getType())) {
            return row;
        }

        FundNavDto fund = fundsByCode.get(String.valueOf(row.getSymbol()).trim().toUpperCase(Locale.ROOT));
        if (fund == null) {
            return row;
        }

        String fundType = firstNonBlank(fund.getFonTurAciklama(), fund.getFundType(), row.getFundType());
        return row.toBuilder()
                .name(firstNonBlank(fund.getFundName(), row.getName()))
                .fundType(fundType)
                .riskDegeri(fund.getRiskDegeri())
                .getiri1a(fund.getGetiri1a())
                .getiri3a(fund.getGetiri3a())
                .getiri6a(fund.getGetiri6a())
                .getiriYb(fund.getGetiriYb())
                .getiri1y(fund.getGetiri1y())
                .getiri3y(fund.getGetiri3y())
                .getiri5y(fund.getGetiri5y())
                .build();
    }

    private String decodeFundType(String instrumentName) {
        if (!hasText(instrumentName) || !instrumentName.contains("||TYPE||")) {
            return null;
        }
        String[] parts = instrumentName.split("\\Q||TYPE||\\E", 2);
        return parts.length == 2 && hasText(parts[1]) ? parts[1].trim() : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private OffsetDateTime asOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

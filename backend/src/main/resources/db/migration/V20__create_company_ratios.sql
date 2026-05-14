CREATE TABLE company_ratios
(
    id                BIGSERIAL PRIMARY KEY,
    company_id        BIGINT         NOT NULL REFERENCES company_profiles (id),
    report_id         BIGINT         NOT NULL REFERENCES company_financial_reports (id),
    price_at_calc     NUMERIC(20, 6),
    market_cap        NUMERIC(30, 2),
    pe_ratio          NUMERIC(15, 4),
    pb_ratio          NUMERIC(15, 4),
    debt_to_equity    NUMERIC(15, 4),
    gross_margin      NUMERIC(10, 6),
    net_margin        NUMERIC(10, 6),
    roe               NUMERIC(10, 6),
    roa               NUMERIC(10, 6),
    revenue_growth    NUMERIC(10, 6),
    net_profit_growth NUMERIC(10, 6),
    asset_growth      NUMERIC(10, 6),
    health_label      VARCHAR(50),
    calculated_at     TIMESTAMPTZ
);

CREATE INDEX idx_company_ratios_company_id ON company_ratios (company_id);
CREATE INDEX idx_company_ratios_report_id ON company_ratios (report_id);

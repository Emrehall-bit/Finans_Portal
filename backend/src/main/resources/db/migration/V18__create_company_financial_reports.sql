CREATE TABLE company_financial_reports
(
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES company_profiles (id),
    period_year     INTEGER,
    period_quarter  INTEGER,
    report_type     VARCHAR(20),
    source_url      TEXT,
    published_at    DATE,
    last_checked_at TIMESTAMPTZ,
    parse_status    VARCHAR(20),
    created_at      TIMESTAMPTZ,
    CONSTRAINT uk_cfr_company_period_type UNIQUE (company_id, period_year, period_quarter, report_type)
);

CREATE INDEX idx_cfr_company_id ON company_financial_reports (company_id);

CREATE TABLE company_financial_values
(
    id              BIGSERIAL PRIMARY KEY,
    report_id       BIGINT        NOT NULL REFERENCES company_financial_reports (id),
    item_key        VARCHAR(100)  NOT NULL,
    raw_label       VARCHAR(500),
    value           NUMERIC(30, 4),
    currency        VARCHAR(10)   NOT NULL DEFAULT 'TRY',
    unit_multiplier INTEGER       NOT NULL DEFAULT 1,
    current_period  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ,
    CONSTRAINT uk_cfv_report_item UNIQUE (report_id, item_key)
);

CREATE INDEX idx_cfv_report_id ON company_financial_values (report_id);

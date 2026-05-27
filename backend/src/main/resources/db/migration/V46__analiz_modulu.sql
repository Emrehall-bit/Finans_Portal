-- Teknik analiz gösterge konfigürasyonları
CREATE TABLE indicator_configs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    instrument_id       BIGINT NOT NULL REFERENCES market_instruments(id),
    indicator_type      VARCHAR(50) NOT NULL,
    parameters          JSONB NOT NULL DEFAULT '{}',
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMPTZ DEFAULT NOW()
);

-- Kullanıcının grafik üzerine çizdiği şekiller
CREATE TABLE chart_drawings (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    instrument_id       BIGINT NOT NULL REFERENCES market_instruments(id),
    timeframe           VARCHAR(10) NOT NULL,
    drawing_type        VARCHAR(50) NOT NULL,
    points              JSONB NOT NULL DEFAULT '[]',
    style               JSONB NOT NULL DEFAULT '{}',
    label               VARCHAR(100),
    is_alert_linked     BOOLEAN DEFAULT FALSE,
    linked_alert_id     BIGINT REFERENCES alerts(id),
    is_premium_feature  BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    updated_at          TIMESTAMPTZ DEFAULT NOW()
);

-- Temel analiz finansal verileri (ham tablo verisi)
CREATE TABLE company_financials (
    id                  BIGSERIAL PRIMARY KEY,
    instrument_id       BIGINT NOT NULL REFERENCES market_instruments(id),
    period              VARCHAR(10) NOT NULL,
    period_type         VARCHAR(10) NOT NULL,
    revenue             NUMERIC(20,2),
    gross_profit        NUMERIC(20,2),
    net_income          NUMERIC(20,2),
    total_assets        NUMERIC(20,2),
    total_equity        NUMERIC(20,2),
    total_liabilities   NUMERIC(20,2),
    current_assets      NUMERIC(20,2),
    current_liabilities NUMERIC(20,2),
    operating_cash_flow NUMERIC(20,2),
    source_url          TEXT,
    data_fetched_at     TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(instrument_id, period, period_type)
);

-- Hesaplanmış temel analiz oranları
CREATE TABLE fundamental_ratios (
    id                      BIGSERIAL PRIMARY KEY,
    instrument_id           BIGINT NOT NULL REFERENCES market_instruments(id),
    period                  VARCHAR(10) NOT NULL,
    calculation_price       NUMERIC(10,4) NOT NULL,
    pe_ratio                NUMERIC(10,4),
    pb_ratio                NUMERIC(10,4),
    graham_number           NUMERIC(10,4),
    gross_margin            NUMERIC(8,4),
    net_margin              NUMERIC(8,4),
    roe                     NUMERIC(8,4),
    roa                     NUMERIC(8,4),
    revenue_growth_yoy      NUMERIC(8,4),
    net_income_growth_yoy   NUMERIC(8,4),
    asset_growth_yoy        NUMERIC(8,4),
    debt_to_equity          NUMERIC(8,4),
    current_ratio           NUMERIC(8,4),
    piotroski_score         INTEGER,
    altman_z_score          NUMERIC(8,4),
    overall_signal          VARCHAR(10),
    calculated_at           TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(instrument_id, period)
);

-- Temel analiz çok dönem trend verisi
CREATE TABLE fundamental_history (
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   BIGINT NOT NULL REFERENCES market_instruments(id),
    period          VARCHAR(10) NOT NULL,
    revenue         NUMERIC(20,2),
    net_income      NUMERIC(20,2),
    gross_margin    NUMERIC(8,4),
    net_margin      NUMERIC(8,4),
    roe             NUMERIC(8,4),
    pe_ratio        NUMERIC(10,4),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_chart_drawings_user_instrument    ON chart_drawings(user_id, instrument_id);
CREATE INDEX idx_indicator_configs_user            ON indicator_configs(user_id, instrument_id);
CREATE INDEX idx_company_financials_instrument     ON company_financials(instrument_id, period_type);
CREATE INDEX idx_fundamental_ratios_instrument     ON fundamental_ratios(instrument_id);
CREATE INDEX idx_fundamental_history_instrument    ON fundamental_history(instrument_id);

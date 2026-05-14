CREATE TABLE company_profiles
(
    id              BIGSERIAL PRIMARY KEY,
    ticker_code     VARCHAR(20)  NOT NULL,
    company_name    VARCHAR(255) NOT NULL,
    sector          VARCHAR(255) NOT NULL,
    market          VARCHAR(255) NOT NULL,
    kap_company_id  VARCHAR(50),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ,
    CONSTRAINT uk_company_profiles_ticker_code UNIQUE (ticker_code)
);

CREATE INDEX idx_company_profiles_ticker_code ON company_profiles (ticker_code);
CREATE INDEX idx_company_profiles_kap_company_id ON company_profiles (kap_company_id);

CREATE TABLE company_disclosures
(
    id               BIGSERIAL PRIMARY KEY,
    company_id       BIGINT      NOT NULL REFERENCES company_profiles (id),
    disclosure_type  VARCHAR(20) NOT NULL,
    title            TEXT,
    kap_url          TEXT,
    published_at     TIMESTAMPTZ,
    summary          TEXT,
    created_at       TIMESTAMPTZ
);

CREATE INDEX idx_company_disclosures_company_id ON company_disclosures (company_id);
CREATE INDEX idx_company_disclosures_published_at ON company_disclosures (published_at DESC);

CREATE TABLE market_macro_indicators (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    source      VARCHAR(50)  NOT NULL,
    frequency   VARCHAR(30)  NOT NULL,
    unit        VARCHAR(30)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP,
    CONSTRAINT uk_macro_indicator_code UNIQUE (code)
);

CREATE TABLE market_macro_observations (
    id               BIGSERIAL   PRIMARY KEY,
    indicator_id     BIGINT      NOT NULL REFERENCES market_macro_indicators(id),
    observation_date DATE        NOT NULL,
    period_label     VARCHAR(20) NOT NULL,
    value            NUMERIC(19, 4) NOT NULL,
    value_type       VARCHAR(30) NOT NULL,
    source           VARCHAR(50) NOT NULL,
    created_at       TIMESTAMP   NOT NULL,
    CONSTRAINT uk_macro_observation UNIQUE (indicator_id, observation_date, value_type)
);

CREATE INDEX idx_macro_obs_date       ON market_macro_observations(observation_date);
CREATE INDEX idx_macro_obs_value_type ON market_macro_observations(value_type);

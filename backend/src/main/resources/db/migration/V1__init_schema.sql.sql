CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    keycloak_id VARCHAR(255) UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL,
    preferred_language VARCHAR(255),
    theme_preference VARCHAR(255),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE portfolios (
    id BIGSERIAL PRIMARY KEY,
    portfolio_name VARCHAR(255) NOT NULL,
    visibility_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_portfolios_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
);

CREATE TABLE portfolio_holdings (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL,
    instrument_code VARCHAR(255) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    buy_price NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_portfolio_holdings_portfolio
        FOREIGN KEY (portfolio_id)
        REFERENCES portfolios (id)
);

CREATE TABLE alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    instrument_code VARCHAR(255) NOT NULL,
    condition_type VARCHAR(255) NOT NULL,
    target_price NUMERIC(19, 4) NOT NULL,
    status VARCHAR(255) NOT NULL,
    triggered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_alerts_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
);

CREATE TABLE watchlist (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    instrument_code VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_watchlist_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),
    CONSTRAINT uk_watchlist_user_instrument
        UNIQUE (user_id, instrument_code)
);

CREATE TABLE news (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(200) NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary TEXT,
    source VARCHAR(100) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    language VARCHAR(10),
    region_scope VARCHAR(20) NOT NULL,
    category VARCHAR(100),
    related_symbol VARCHAR(30),
    url VARCHAR(1200) NOT NULL,
    published_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_news_external_id
        UNIQUE (external_id)
);

CREATE INDEX idx_portfolios_user_id
    ON portfolios (user_id);

CREATE INDEX idx_portfolio_holdings_portfolio_id
    ON portfolio_holdings (portfolio_id);

CREATE INDEX idx_alerts_user_id_created_at
    ON alerts (user_id, created_at DESC);

CREATE INDEX idx_alerts_status
    ON alerts (status);

CREATE INDEX idx_watchlist_user_id
    ON watchlist (user_id);

CREATE INDEX idx_news_published_at
    ON news (published_at);

CREATE INDEX idx_news_provider
    ON news (provider);

CREATE INDEX idx_news_region_scope
    ON news (region_scope);

CREATE INDEX idx_news_category
    ON news (category);

CREATE INDEX idx_news_related_symbol
    ON news (related_symbol);

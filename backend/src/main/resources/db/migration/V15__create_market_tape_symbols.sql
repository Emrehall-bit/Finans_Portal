CREATE TABLE market_tape_symbols
(
    id            BIGSERIAL PRIMARY KEY,
    symbol        VARCHAR(40) NOT NULL UNIQUE,
    display_order INTEGER     NOT NULL,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_market_tape_symbols_display_order
    ON market_tape_symbols (display_order);

INSERT INTO market_tape_symbols (symbol, display_order, enabled)
VALUES ('XU100', 0, TRUE),
       ('BIST100', 1, TRUE),
       ('BTCUSDT', 2, TRUE),
       ('BTCTRY', 3, TRUE),
       ('BTC', 4, TRUE),
       ('USDTRY', 5, TRUE),
       ('EURTRY', 6, TRUE),
       ('XAUTRY', 7, TRUE),
       ('GRAMALTIN', 8, TRUE),
       ('ETHUSDT', 9, TRUE),
       ('ETHTRY', 10, TRUE),
       ('ETH', 11, TRUE);

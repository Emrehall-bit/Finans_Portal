CREATE INDEX idx_market_instruments_instrument_code
    ON public.market_instruments (instrument_code);

CREATE INDEX idx_market_instruments_source_name
    ON public.market_instruments (source_name);

CREATE INDEX idx_market_prices_instrument_id
    ON public.market_prices (instrument_id);

CREATE INDEX idx_market_prices_price_timestamp
    ON public.market_prices (price_timestamp);

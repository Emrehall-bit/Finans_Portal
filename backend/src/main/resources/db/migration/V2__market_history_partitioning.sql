DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'market_history'
    ) THEN
        ALTER TABLE public.market_history RENAME TO market_history_legacy;
    END IF;
END $$;

CREATE TABLE public.market_history (
    id BIGSERIAL NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    source VARCHAR(30) NOT NULL,
    price_date DATE NOT NULL,
    close_price NUMERIC(20,8) NOT NULL,
    open_price NUMERIC(20,8),
    high_price NUMERIC(20,8),
    low_price NUMERIC(20,8),
    volume NUMERIC(30,8),
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT market_history_partitioned_pkey PRIMARY KEY (id, price_date)
) PARTITION BY RANGE (price_date);

CREATE TABLE public.market_history_2000_2009
    PARTITION OF public.market_history
    FOR VALUES FROM ('2000-01-01') TO ('2010-01-01');

CREATE TABLE public.market_history_2010_2019
    PARTITION OF public.market_history
    FOR VALUES FROM ('2010-01-01') TO ('2020-01-01');

CREATE TABLE public.market_history_2020_2024
    PARTITION OF public.market_history
    FOR VALUES FROM ('2020-01-01') TO ('2025-01-01');

CREATE TABLE public.market_history_2025_2029
    PARTITION OF public.market_history
    FOR VALUES FROM ('2025-01-01') TO ('2030-01-01');

CREATE TABLE public.market_history_default
    PARTITION OF public.market_history DEFAULT;

CREATE UNIQUE INDEX uq_market_history_2000_2009_symbol_source_price_date
    ON public.market_history_2000_2009 (symbol, source, price_date);
CREATE UNIQUE INDEX uq_market_history_2010_2019_symbol_source_price_date
    ON public.market_history_2010_2019 (symbol, source, price_date);
CREATE UNIQUE INDEX uq_market_history_2020_2024_symbol_source_price_date
    ON public.market_history_2020_2024 (symbol, source, price_date);
CREATE UNIQUE INDEX uq_market_history_2025_2029_symbol_source_price_date
    ON public.market_history_2025_2029 (symbol, source, price_date);
CREATE UNIQUE INDEX uq_market_history_default_symbol_source_price_date
    ON public.market_history_default (symbol, source, price_date);

CREATE INDEX idx_market_history_2000_2009_symbol_source_price_date_desc
    ON public.market_history_2000_2009 (symbol, source, price_date DESC);
CREATE INDEX idx_market_history_2010_2019_symbol_source_price_date_desc
    ON public.market_history_2010_2019 (symbol, source, price_date DESC);
CREATE INDEX idx_market_history_2020_2024_symbol_source_price_date_desc
    ON public.market_history_2020_2024 (symbol, source, price_date DESC);
CREATE INDEX idx_market_history_2025_2029_symbol_source_price_date_desc
    ON public.market_history_2025_2029 (symbol, source, price_date DESC);
CREATE INDEX idx_market_history_default_symbol_source_price_date_desc
    ON public.market_history_default (symbol, source, price_date DESC);

CREATE INDEX idx_market_history_2000_2009_price_date_desc
    ON public.market_history_2000_2009 (price_date DESC);
CREATE INDEX idx_market_history_2010_2019_price_date_desc
    ON public.market_history_2010_2019 (price_date DESC);
CREATE INDEX idx_market_history_2020_2024_price_date_desc
    ON public.market_history_2020_2024 (price_date DESC);
CREATE INDEX idx_market_history_2025_2029_price_date_desc
    ON public.market_history_2025_2029 (price_date DESC);
CREATE INDEX idx_market_history_default_price_date_desc
    ON public.market_history_default (price_date DESC);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'market_history_legacy'
    ) THEN
        INSERT INTO public.market_history (
            id,
            symbol,
            source,
            price_date,
            close_price,
            open_price,
            high_price,
            low_price,
            volume,
            created_at
        )
        SELECT
            id,
            symbol,
            source,
            price_date,
            close_price,
            NULL,
            NULL,
            NULL,
            NULL,
            created_at
        FROM public.market_history_legacy;

        PERFORM setval(
            pg_get_serial_sequence('public.market_history', 'id'),
            COALESCE((SELECT MAX(id) FROM public.market_history), 1),
            true
        );

        DROP TABLE public.market_history_legacy;
    END IF;
END $$;

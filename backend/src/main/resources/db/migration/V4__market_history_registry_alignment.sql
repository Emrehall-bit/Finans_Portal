ALTER TABLE public.market_history
    ADD COLUMN IF NOT EXISTS display_name character varying(255),
    ADD COLUMN IF NOT EXISTS instrument_type character varying(50),
    ADD COLUMN IF NOT EXISTS currency character varying(20);

UPDATE public.market_history
SET display_name = COALESCE(display_name, symbol),
    instrument_type = COALESCE(
            instrument_type,
            CASE source
                WHEN 'BINANCE' THEN 'CRYPTO'
                WHEN 'BIST' THEN 'STOCK'
                WHEN 'EVDS' THEN 'FOREX'
                ELSE 'UNKNOWN'
                END
        ),
    currency = COALESCE(
            currency,
            CASE
                WHEN source = 'BINANCE' AND symbol LIKE '%USDT' THEN 'USDT'
                WHEN source IN ('BIST', 'EVDS') THEN 'TRY'
                ELSE NULL
                END
        );

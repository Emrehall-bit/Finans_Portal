DELETE FROM public.market_prices
WHERE instrument_id IN (
    SELECT id
    FROM public.market_instruments
    WHERE instrument_code IS NULL
       OR BTRIM(instrument_code) = ''
);

DELETE FROM public.market_instruments
WHERE instrument_code IS NULL
   OR BTRIM(instrument_code) = '';

UPDATE public.market_provider_mappings
SET source = 'EVDS',
    external_symbol = 'TP_TUKFIY2025_GENEL-1'
WHERE instrument_id = (
    SELECT id
    FROM public.market_instruments
    WHERE symbol = 'TCMBTUFEAYLIK'
)
  AND (source IS NULL OR external_symbol IS NULL OR btrim(external_symbol) = '');

UPDATE public.market_provider_mappings
SET source = 'EVDS',
    external_symbol = 'TP_TUKFIY2025_GENEL-3'
WHERE instrument_id = (
    SELECT id
    FROM public.market_instruments
    WHERE symbol = 'TCMBTUFEYILLIK'
)
  AND (source IS NULL OR external_symbol IS NULL OR btrim(external_symbol) = '');

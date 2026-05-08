UPDATE public.market_provider_mappings mpm
SET source = 'EVDS',
    external_symbol = 'TP_TUKFIY2025_GENEL-1'
FROM public.market_instruments mi
WHERE mpm.instrument_id = mi.id
  AND mi.symbol = 'TCMBTUFEAYLIK'
  AND (mpm.source IS NULL OR btrim(mpm.source::text) = '');

UPDATE public.market_provider_mappings mpm
SET source = 'EVDS',
    external_symbol = 'TP_TUKFIY2025_GENEL-3'
FROM public.market_instruments mi
WHERE mpm.instrument_id = mi.id
  AND mi.symbol = 'TCMBTUFEYILLIK'
  AND (mpm.source IS NULL OR btrim(mpm.source::text) = '');

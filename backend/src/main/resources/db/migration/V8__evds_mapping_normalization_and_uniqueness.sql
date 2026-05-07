ALTER TABLE public.market_provider_mappings
    DROP CONSTRAINT IF EXISTS uk_market_provider_mappings_source_symbol;

DROP INDEX IF EXISTS uk_market_provider_mappings_source_symbol_non_evds;
DROP INDEX IF EXISTS uk_market_provider_mappings_evds_instrument_symbol;

UPDATE public.market_provider_mappings
SET external_symbol = regexp_replace(external_symbol, '-\d+$', '')
WHERE source = 'EVDS'
  AND external_symbol ~ '-\d+$';

CREATE UNIQUE INDEX IF NOT EXISTS uk_market_provider_mappings_source_symbol_non_evds
    ON public.market_provider_mappings (source, external_symbol)
    WHERE source <> 'EVDS';

CREATE UNIQUE INDEX IF NOT EXISTS uk_market_provider_mappings_evds_instrument_symbol
    ON public.market_provider_mappings (source, instrument_id, external_symbol)
    WHERE source = 'EVDS';

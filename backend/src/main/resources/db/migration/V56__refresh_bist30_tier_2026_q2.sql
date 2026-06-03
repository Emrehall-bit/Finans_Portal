-- Refresh BIST30 tier assignments for 2026 Q2.
-- This migration only updates existing STOCK instruments in market_instruments.
-- It does not create missing instruments and does not touch price/history tables.

WITH target_bist30(symbol) AS (
    VALUES
        ('AEFES'),
        ('AKBNK'),
        ('ASELS'),
        ('ASTOR'),
        ('BIMAS'),
        ('DSTKF'),
        ('EKGYO'),
        ('ENKAI'),
        ('EREGL'),
        ('FROTO'),
        ('GARAN'),
        ('GUBRF'),
        ('ISCTR'),
        ('KCHOL'),
        ('KRDMD'),
        ('MGROS'),
        ('PETKM'),
        ('PGSUS'),
        ('SAHOL'),
        ('SASA'),
        ('SISE'),
        ('TAVHL'),
        ('TCELL'),
        ('THYAO'),
        ('TOASO'),
        ('TRALT'),
        ('TTKOM'),
        ('TUPRS'),
        ('VAKBN'),
        ('YKBNK')
)
UPDATE market_instruments mi
SET bist_tier = 'BIST30'
FROM target_bist30 target
WHERE mi.instrument_type = 'STOCK'
  AND upper(mi.instrument_code) = target.symbol;

-- Optional diagnostic query for manual checks after migration:
-- WITH target_bist30(symbol) AS (
--     VALUES
--         ('AEFES'), ('AKBNK'), ('ASELS'), ('ASTOR'), ('BIMAS'), ('DSTKF'),
--         ('EKGYO'), ('ENKAI'), ('EREGL'), ('FROTO'), ('GARAN'), ('GUBRF'),
--         ('ISCTR'), ('KCHOL'), ('KRDMD'), ('MGROS'), ('PETKM'), ('PGSUS'),
--         ('SAHOL'), ('SASA'), ('SISE'), ('TAVHL'), ('TCELL'), ('THYAO'),
--         ('TOASO'), ('TRALT'), ('TTKOM'), ('TUPRS'), ('VAKBN'), ('YKBNK')
-- )
-- SELECT target.symbol
-- FROM target_bist30 target
-- LEFT JOIN market_instruments mi
--   ON mi.instrument_type = 'STOCK'
--  AND upper(mi.instrument_code) = target.symbol
-- WHERE mi.id IS NULL
-- ORDER BY target.symbol;

ALTER TABLE company_ratios
    ADD COLUMN IF NOT EXISTS revenue_growth_label VARCHAR(80),
    ADD COLUMN IF NOT EXISTS net_profit_growth_label VARCHAR(80),
    ADD COLUMN IF NOT EXISTS asset_growth_label VARCHAR(80);

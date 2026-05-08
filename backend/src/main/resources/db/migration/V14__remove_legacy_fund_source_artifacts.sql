delete from market_data_backfill_status
where symbol = 'TEFAS';

delete from market_history
where source = 'TEFAS';

delete from market_provider_mappings
where provider_source = 'TEFAS'
   or source = 'TEFAS';

drop table if exists fund_info;

delete from market_instruments instrument
where instrument.type = 'FUND'
  and not exists (
    select 1
    from market_provider_mappings mapping
    where mapping.instrument_id = instrument.id
)
  and not exists (
    select 1
    from market_history history
    where history.symbol = instrument.symbol
);
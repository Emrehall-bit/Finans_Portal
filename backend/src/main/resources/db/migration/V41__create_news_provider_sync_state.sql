create table if not exists news_provider_sync_state (
    provider varchar(100) primary key,
    last_successful_sync_at timestamp
);

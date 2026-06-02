CREATE TABLE IF NOT EXISTS news_favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    news_id BIGINT NOT NULL REFERENCES news(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_news_favorites_user_news UNIQUE (user_id, news_id)
);

CREATE INDEX IF NOT EXISTS idx_news_favorites_user_id ON news_favorites(user_id);
CREATE INDEX IF NOT EXISTS idx_news_favorites_news_id ON news_favorites(news_id);

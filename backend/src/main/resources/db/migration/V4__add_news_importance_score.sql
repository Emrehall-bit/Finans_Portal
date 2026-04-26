ALTER TABLE news
    ADD COLUMN IF NOT EXISTS importance_score INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_news_importance_score ON news (importance_score);

-- Per-subscriber word interaction tracking
-- Unified view of how each user interacts with each word across all features

ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS feed_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS crush_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS last_quizzed_at TIMESTAMPTZ;
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS last_fed_at TIMESTAMPTZ;
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS last_crushed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_sent_words_quiz_recency ON sent_words (chat_id, last_quizzed_at ASC NULLS FIRST);
CREATE INDEX IF NOT EXISTS idx_sent_words_feed_recency ON sent_words (chat_id, last_fed_at ASC NULLS FIRST);

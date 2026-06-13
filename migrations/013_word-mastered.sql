-- Allow users to mark words as "mastered" to exclude from quiz/feed/crush
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS mastered BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS mastered_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_sent_words_mastered ON sent_words (chat_id, mastered);

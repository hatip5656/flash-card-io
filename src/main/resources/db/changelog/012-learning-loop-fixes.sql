--liquibase formatted sql

--changeset hatip:012-activity-columns
ALTER TABLE activity_log ADD COLUMN IF NOT EXISTS games_played INTEGER DEFAULT 0;
ALTER TABLE activity_log ADD COLUMN IF NOT EXISTS phrases_seen INTEGER DEFAULT 0;
ALTER TABLE activity_log ADD COLUMN IF NOT EXISTS stories_read INTEGER DEFAULT 0;
ALTER TABLE activity_log ADD COLUMN IF NOT EXISTS dialogs_completed INTEGER DEFAULT 0;

--changeset hatip:012-sent-dialogs
CREATE TABLE IF NOT EXISTS sent_dialogs (
    chat_id BIGINT NOT NULL,
    dialog_id INTEGER NOT NULL,
    completed_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (chat_id, dialog_id)
);
CREATE INDEX IF NOT EXISTS idx_sent_dialogs_chat ON sent_dialogs (chat_id);

--changeset hatip:012-phrase-times-seen
ALTER TABLE sent_phrases ADD COLUMN IF NOT EXISTS times_seen INTEGER DEFAULT 1;

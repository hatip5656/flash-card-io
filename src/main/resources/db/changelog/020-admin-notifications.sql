--liquibase formatted sql

--changeset admin-notifications:1
CREATE TABLE IF NOT EXISTS admin_notifications (
    id          BIGSERIAL PRIMARY KEY,
    type        TEXT NOT NULL,           -- info, success, warning, error
    category    TEXT NOT NULL,           -- candidates, podcasts, grammar, system, words
    title       TEXT NOT NULL,
    message     TEXT,
    metadata    JSONB DEFAULT '{}',     -- extra data (counts, ids, etc.)
    read        BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_admin_notifications_read ON admin_notifications(read);
CREATE INDEX idx_admin_notifications_created ON admin_notifications(created_at DESC);
CREATE INDEX idx_admin_notifications_category ON admin_notifications(category);

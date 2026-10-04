--liquibase formatted sql

--changeset account-linking:1
CREATE TABLE IF NOT EXISTS link_codes (
    code TEXT PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS linked_accounts (
    mobile_id BIGINT NOT NULL,
    telegram_id BIGINT NOT NULL,
    linked_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (mobile_id, telegram_id)
);

CREATE INDEX IF NOT EXISTS idx_linked_telegram ON linked_accounts(telegram_id);

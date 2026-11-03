--liquibase formatted sql

--changeset auth:1
-- Sign-in accounts (Google today) attached to a learner. One account of each provider per learner,
-- and an account belongs to exactly one learner.
CREATE TABLE user_identities (
    provider    TEXT NOT NULL,
    subject     TEXT NOT NULL,
    chat_id     BIGINT NOT NULL,
    email       TEXT,
    name        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ,
    PRIMARY KEY (provider, subject),
    UNIQUE (chat_id, provider)
);

-- App sessions issued at sign-in; only a SHA-256 hash of the token is stored
CREATE TABLE auth_sessions (
    token_hash   TEXT PRIMARY KEY,
    chat_id      BIGINT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_auth_sessions_chat ON auth_sessions (chat_id);

--changeset auth:2 splitStatements:false
-- Mobile learner ids came from MAX(chat_id)+1: two sign-ups at once got the same id, and deleting the
-- newest learner handed their id (and any sign-in account still pointing at it) to the next one
CREATE SEQUENCE mobile_user_id_seq;
SELECT setval('mobile_user_id_seq', GREATEST((SELECT COALESCE(MAX(chat_id), 0) FROM subscribers), 2000000000 - 1) + 1, false);

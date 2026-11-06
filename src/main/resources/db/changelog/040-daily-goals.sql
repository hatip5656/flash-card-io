--liquibase formatted sql

--changeset daily-goals:1
-- A learner's daily goal choices. No row means the defaults (every task on, notifications on).
CREATE TABLE daily_goal_settings (
    chat_id          BIGINT PRIMARY KEY,
    words_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    words_target     INTEGER NOT NULL DEFAULT 10,
    quiz_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    quiz_target      INTEGER NOT NULL DEFAULT 20,
    reviews_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    grammar_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    notify_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    morning_time     TIME NOT NULL DEFAULT '09:00',
    evening_time     TIME NOT NULL DEFAULT '20:00',
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One goal per learner per (Tallinn) day. Targets are fixed when the row is created.
CREATE TABLE daily_goals (
    chat_id            BIGINT NOT NULL,
    goal_date          DATE NOT NULL,
    words_target       INTEGER NOT NULL,
    quiz_target        INTEGER NOT NULL,
    reviews_target     INTEGER NOT NULL,
    grammar_lesson_id  TEXT,
    quiz_done          INTEGER NOT NULL DEFAULT 0,
    reviews_done       INTEGER NOT NULL DEFAULT 0,
    completed_at       TIMESTAMPTZ,
    morning_sent_at    TIMESTAMPTZ,
    evening_sent_at    TIMESTAMPTZ,
    done_sent_at       TIMESTAMPTZ,
    PRIMARY KEY (chat_id, goal_date)
);

-- FCM registration tokens; a learner can have several devices.
CREATE TABLE push_devices (
    token         TEXT PRIMARY KEY,
    chat_id       BIGINT NOT NULL,
    platform      TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_push_devices_chat ON push_devices (chat_id);

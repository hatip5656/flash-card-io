--liquibase formatted sql

--changeset flashcard:001 splitStatements:true
-- Old Node.js migration tracker — keep it so the legacy system doesn't conflict
CREATE TABLE IF NOT EXISTS _migrations (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

--changeset flashcard:002 splitStatements:true
CREATE TABLE IF NOT EXISTS subscribers (
    chat_id BIGINT PRIMARY KEY,
    channel TEXT NOT NULL DEFAULT 'telegram',
    cefr_level TEXT NOT NULL DEFAULT 'A1',
    schedule TEXT NOT NULL DEFAULT '0 9 * * *',
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    username TEXT,
    first_name TEXT,
    preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
    next_delivery_at TIMESTAMPTZ,
    next_grammar_at TIMESTAMPTZ
);

--changeset flashcard:003 splitStatements:true
CREATE TABLE IF NOT EXISTS sent_words (
    chat_id BIGINT NOT NULL,
    word_id TEXT NOT NULL,
    word_value TEXT,
    sent_at TIMESTAMPTZ DEFAULT NOW(),
    english TEXT,
    quiz_count INTEGER DEFAULT 0,
    ease_factor REAL DEFAULT 2.5,
    interval_days INTEGER DEFAULT 1,
    next_review DATE DEFAULT CURRENT_DATE,
    repetitions INTEGER DEFAULT 0,
    PRIMARY KEY (chat_id, word_id)
);

--changeset flashcard:004 splitStatements:true
CREATE TABLE IF NOT EXISTS sent_grammar (
    chat_id BIGINT NOT NULL,
    lesson_id TEXT NOT NULL,
    sent_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (chat_id, lesson_id)
);

--changeset flashcard:005 splitStatements:true
CREATE TABLE IF NOT EXISTS quiz_results (
    id SERIAL PRIMARY KEY,
    chat_id BIGINT,
    score INTEGER,
    total INTEGER,
    percentage INTEGER,
    completed_at TIMESTAMPTZ DEFAULT NOW()
);

--changeset flashcard:006 splitStatements:true
CREATE TABLE IF NOT EXISTS quiz_answers (
    id SERIAL PRIMARY KEY,
    quiz_id INTEGER REFERENCES quiz_results(id),
    estonian TEXT,
    correct_answer TEXT,
    user_answer TEXT,
    is_correct BOOLEAN
);

--changeset flashcard:007 splitStatements:true
CREATE TABLE IF NOT EXISTS activity_log (
    id SERIAL PRIMARY KEY,
    chat_id BIGINT,
    activity_date DATE DEFAULT CURRENT_DATE,
    words_learned INTEGER DEFAULT 0,
    quizzes_taken INTEGER DEFAULT 0,
    UNIQUE (chat_id, activity_date)
);

--changeset flashcard:008 splitStatements:true
CREATE TABLE IF NOT EXISTS saved_words (
    chat_id BIGINT NOT NULL,
    word_id TEXT NOT NULL,
    saved_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (chat_id, word_id)
);

--changeset flashcard:009 splitStatements:true
CREATE TABLE IF NOT EXISTS story_reads (
    chat_id BIGINT NOT NULL,
    story_id TEXT NOT NULL,
    read_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (chat_id, story_id)
);

--changeset flashcard:010 splitStatements:true
CREATE TABLE IF NOT EXISTS word_comments (
    id SERIAL PRIMARY KEY,
    chat_id BIGINT,
    word_id TEXT,
    comment TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

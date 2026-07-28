--liquibase formatted sql

--changeset flashcard:050 splitStatements:true
CREATE TABLE IF NOT EXISTS grammar_lessons (
    id TEXT PRIMARY KEY,
    cefr_level TEXT NOT NULL,
    topic TEXT NOT NULL,
    topic_tr TEXT,
    content TEXT NOT NULL,
    content_tr TEXT
);

--liquibase formatted sql

--changeset flashcard:022 splitStatements:true
CREATE TABLE IF NOT EXISTS dialogs (
    id TEXT PRIMARY KEY,
    title TEXT,
    title_tr TEXT,
    cefr_level TEXT,
    category TEXT,
    situation TEXT,
    situation_tr TEXT,
    icon TEXT,
    sort_order INTEGER DEFAULT 0
);

--changeset flashcard:023 splitStatements:true
CREATE TABLE IF NOT EXISTS dialog_lines (
    id SERIAL PRIMARY KEY,
    dialog_id TEXT REFERENCES dialogs(id) ON DELETE CASCADE,
    speaker TEXT,
    estonian TEXT,
    english TEXT,
    turkish TEXT,
    sort_order INTEGER DEFAULT 0
);

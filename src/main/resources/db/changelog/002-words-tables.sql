--liquibase formatted sql

--changeset flashcard:011 splitStatements:true
CREATE TABLE IF NOT EXISTS words (
    id TEXT PRIMARY KEY,
    estonian TEXT,
    english TEXT,
    turkish TEXT,
    cefr_level TEXT,
    image_query TEXT
);

--changeset flashcard:012 splitStatements:true
CREATE TABLE IF NOT EXISTS word_sentences (
    id SERIAL PRIMARY KEY,
    word_id TEXT REFERENCES words(id) ON DELETE CASCADE,
    estonian TEXT,
    english TEXT,
    turkish TEXT,
    sort_order INTEGER DEFAULT 0
);

--changeset flashcard:013 splitStatements:true
CREATE TABLE IF NOT EXISTS grammar_stories (
    id TEXT PRIMARY KEY,
    cefr_level TEXT,
    topic TEXT,
    icon TEXT
);

--changeset flashcard:014 splitStatements:true
CREATE TABLE IF NOT EXISTS grammar_story_slides (
    id SERIAL PRIMARY KEY,
    story_id TEXT REFERENCES grammar_stories(id) ON DELETE CASCADE,
    title TEXT,
    body TEXT,
    highlight TEXT,
    sort_order INTEGER DEFAULT 0
);

--changeset flashcard:015 splitStatements:true
CREATE TABLE IF NOT EXISTS grammar_slide_examples (
    id SERIAL PRIMARY KEY,
    slide_id INTEGER REFERENCES grammar_story_slides(id) ON DELETE CASCADE,
    estonian TEXT,
    english TEXT,
    turkish TEXT,
    sort_order INTEGER DEFAULT 0
);

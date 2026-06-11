-- Words and sentences tables: move word data from JSON files to DB

CREATE TABLE IF NOT EXISTS words (
  id TEXT PRIMARY KEY,
  estonian TEXT NOT NULL,
  english TEXT NOT NULL,
  turkish TEXT,
  cefr_level TEXT NOT NULL,
  image_query TEXT
);

CREATE INDEX IF NOT EXISTS idx_words_cefr ON words (cefr_level);

CREATE TABLE IF NOT EXISTS word_sentences (
  id SERIAL PRIMARY KEY,
  word_id TEXT NOT NULL REFERENCES words(id) ON DELETE CASCADE,
  estonian TEXT NOT NULL,
  english TEXT NOT NULL,
  turkish TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_word_sentences_word ON word_sentences (word_id, sort_order);

-- Grammar stories
CREATE TABLE IF NOT EXISTS grammar_stories (
  id TEXT PRIMARY KEY,
  cefr_level TEXT NOT NULL,
  topic TEXT NOT NULL,
  icon TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_grammar_stories_cefr ON grammar_stories (cefr_level);

CREATE TABLE IF NOT EXISTS grammar_story_slides (
  id SERIAL PRIMARY KEY,
  story_id TEXT NOT NULL REFERENCES grammar_stories(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  highlight TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_grammar_slides_story ON grammar_story_slides (story_id, sort_order);

CREATE TABLE IF NOT EXISTS grammar_slide_examples (
  id SERIAL PRIMARY KEY,
  slide_id INTEGER NOT NULL REFERENCES grammar_story_slides(id) ON DELETE CASCADE,
  estonian TEXT NOT NULL,
  english TEXT NOT NULL,
  turkish TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0
);
